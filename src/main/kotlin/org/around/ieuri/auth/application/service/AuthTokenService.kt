package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.command.LoginCommand
import org.around.ieuri.auth.application.command.LoginEmailCommand
import org.around.ieuri.auth.application.command.LogoutCommand
import org.around.ieuri.auth.application.command.RefreshTokenCommand
import org.around.ieuri.auth.application.exception.InactiveMemberException
import org.around.ieuri.auth.application.exception.InvalidCredentialsException
import org.around.ieuri.auth.application.exception.InvalidTokenException
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.exception.TokenReuseDetectedException
import org.around.ieuri.auth.application.port.`in`.command.AuthTokenUseCase
import org.around.ieuri.auth.application.port.`in`.command.LogoutResult
import org.around.ieuri.auth.application.port.`in`.command.TokenPairResult
import org.around.ieuri.auth.application.port.out.client.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.security.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.port.out.security.PasswordEncoderPort
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.ProviderUserId
import org.springframework.stereotype.Service
import org.around.ieuri.auth.application.port.out.event.DomainEventPublisherPort
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 인증 토큰 계열 서비스. SNS 로그인·refresh 회전·로그아웃을 한 서비스로 묶어 관리한다.
 * 토큰 발급 자체는 [TokenIssuer]에 위임하며, 로그인·재발급 양쪽이 이를 재사용한다.
 */
@Service
class AuthTokenService(
    private val snsVerifier: SnsIdentityVerifierPort,
    private val memberRepository: MemberRepositoryPort,
    private val tokenProvider: TokenProviderPort,
    private val refreshTokenStore: RefreshTokenStorePort,
    private val tokenIssuer: TokenIssuer,
    private val passwordEncoder: PasswordEncoderPort,
    private val identifierProtector: LoginIdentifierProtectorPort,
    private val providerTokenCollector: ProviderTokenCollector,
    private val eventPublisher: DomainEventPublisherPort,
    private val clock: Clock,
) : AuthTokenUseCase {

    /**
     * SNS 인증 → 기존 회원 조회 → 상태 확인 → 토큰 발급.
     * 미가입 회원은 거부(회원가입으로 유도). 정지 회원은 거부하되,
     * **기간제 정지가 만료됐으면 자동 복구** 후 로그인을 허용한다.
     */
    @Transactional
    override fun loginBySns(command: LoginCommand): TokenPairResult {
        val now = clock.instant()
        val identity = snsVerifier.verify(command.provider, command.idToken)
        val providerUserId = ProviderUserId(identity.providerUserId)

        val authentication = memberRepository.findAuthentication(command.provider, providerUserId)
            ?: throw MemberNotFoundException("가입되지 않은 계정입니다. 회원가입이 필요합니다.")

        ensureLoginable(
            authentication.memberId, authentication.status, authentication.suspendedUntil, authentication.suspensionReason, now,
        )

        // 탈퇴 시 연결 해제용 토큰 보충. 실패해도 로그인은 성공한다(R7).
        providerTokenCollector.collect(authentication.memberId, command.provider, command.authorizationCode)

        publishAccess(authentication.memberId, AccessType.LOGIN, command.ipAddress)
        return tokenIssuer.issueFor(authentication.memberId)
    }

    /**
     * 자체(EMAIL) 로그인. 이메일로 회원을 찾아 비밀번호와 상태를 확인한 뒤 토큰을 발급한다.
     * 계정 존재 여부를 드러내지 않도록 실패 메시지는 통일한다(InvalidCredentials).
     */
    @Transactional
    override fun loginByEmail(command: LoginEmailCommand): TokenPairResult {
        val now = clock.instant()
        val email = Email(command.email).value

        val auth = memberRepository.findEmailAuthentication(identifierProtector.protect(email))
            ?: throw InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.")
        // passwordHash가 없으면 SNS 계정 → 이메일 로그인 불가(존재 여부는 드러내지 않음).
        val passwordHash = auth.passwordHash
            ?: throw InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.")
        if (!passwordEncoder.matches(command.password, passwordHash)) {
            throw InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.")
        }
        // **이메일 미인증을 여기서 막지 않는다**(SPEC-0024 R8). 미인증 회원도 토큰을 받고,
        // 차단은 오퍼레이션 단계의 게이트가 맡는다(R9) — 그래야 인증 화면에 머무는 동안
        // 세션이 유지된다. SNS 로그인에는 원래 이 검사가 없었으므로 두 경로가 같아진다.
        ensureLoginable(auth.memberId, auth.status, auth.suspendedUntil, auth.suspensionReason, now)

        publishAccess(auth.memberId, AccessType.LOGIN, command.ipAddress)
        return tokenIssuer.issueFor(auth.memberId)
    }

    /**
     * 로그인 가능 상태인지 확인한다. 기간제 정지가 만료됐으면 자동 복구 후 통과시키고,
     * 유효한 정지면 [InactiveMemberException]을 던진다. SNS·EMAIL 로그인이 공유한다.
     */
    private fun ensureLoginable(
        memberId: Long,
        status: MemberStatus,
        suspendedUntil: Instant?,
        suspensionReason: String?,
        now: Instant,
    ) {
        when (status) {
            MemberStatus.ACTIVE -> Unit
            MemberStatus.SUSPENDED -> {
                if (suspendedUntil != null && !now.isBefore(suspendedUntil)) {
                    // 기간제 정지 만료 → 자동 복구 후 로그인 허용.
                    val member = memberRepository.load(memberId)
                        ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
                    member.reactivate(now)
                    memberRepository.updateStatus(member)
                } else {
                    val reason = suspensionReason?.let { " 사유: $it" } ?: ""
                    throw InactiveMemberException("정지된 계정입니다.$reason")
                }
            }
        }
    }

    /**
     * refresh 토큰으로 accessToken 재발급(회전).
     * 저장소에 살아있는 jti만 통과시키고, 이미 회전/폐기된 refresh 재사용은 탈취로 보고 회원 전체를 폐기한다.
     *
     * **발급이 폐기보다 먼저다**(SPEC-0032 R5). 순서가 이 방향인 것이 이 메서드의 정확성을
     * 좌우한다 — 리프레시 저장소는 Valkey이고 회원 정보는 PostgreSQL이라 `@Transactional`이
     * Valkey 연산을 되돌리지 못하므로, 원자성을 트랜잭션이 아니라 **순서**로 확보한다.
     *
     * 폐기를 먼저 하면 발급이 실패했을 때 **구 토큰은 죽고 신 토큰은 없는** 상태가 되고,
     * 사용자가 재시도하면 `existsActive`가 false라 탈취로 오판되어 전 기기가 로그아웃된다.
     * 반대로 이 순서에서는 중간 실패 시 두 토큰이 잠시 함께 유효하지만, 구 토큰은 TTL로
     * 만료된다 — 세션이 영구히 죽는 것보다 낫다.
     */
    override fun refresh(command: RefreshTokenCommand): TokenPairResult {
        // 서명/만료/타입 검증 실패 시 InvalidTokenException.
        val claims = tokenProvider.parseRefreshToken(command.refreshToken)

        if (!refreshTokenStore.existsActive(claims.jti)) {
            // 재사용 감지: 이미 회전·폐기된 refresh → 해당 회원 전체 폐기.
            refreshTokenStore.revokeAllByMember(claims.memberId)
            throw TokenReuseDetectedException("재사용된 리프레시 토큰입니다. 다시 로그인해 주세요.")
        }

        // 회전: 신 토큰을 먼저 저장하고(issueFor가 저장까지 한다), 성공한 뒤에 구 토큰을 폐기한다.
        val issued = tokenIssuer.issueFor(claims.memberId)
        refreshTokenStore.revoke(claims.jti)
        return issued
    }

    /**
     * 로그아웃. 제출된 refresh의 jti를 저장소에서 폐기한다.
     * 이미 무효/만료된 토큰이어도 멱등하게 성공 처리한다. accessToken은 짧은 만료로 자연 소멸한다.
     */
    override fun logout(command: LogoutCommand): LogoutResult {
        val claims = try {
            tokenProvider.parseRefreshToken(command.refreshToken)
        } catch (ex: InvalidTokenException) {
            return LogoutResult(success = true)
        }
        refreshTokenStore.revoke(claims.jti)

        publishAccess(claims.memberId, AccessType.LOGOUT, command.ipAddress)
        return LogoutResult(success = true)
    }

    /**
     * 접속기록 발행(「개인정보의 안전성 확보조치 기준」 제8조).
     *
     * **성공한 접속만 남긴다** — 실패한 로그인 시도는 이 기록의 대상이 아니다(계정 존재 여부를
     * 드러내지 않으려 실패 경로가 회원을 특정하지 않기도 한다).
     *
     * 저장은 리스너가 별도 스레드에서 한다(`MemberAccessAuditListener`) — 사용자가 기다리는
     * 응답 경로에 INSERT를 두지 않는다.
     */
    private fun publishAccess(memberId: Long, accessType: AccessType, ipAddress: String?) {
        eventPublisher.publish(MemberAccessed(memberId, accessType, ipAddress))
    }
}
