package org.around.ieuri.auth.application.token
import org.around.ieuri.auth.application.member.ProviderTokenCollector
import org.around.ieuri.auth.application.member.provided.exception.InactiveMemberException
import org.around.ieuri.auth.application.member.provided.exception.InvalidCredentialsException
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.DomainEventPublisherPort
import org.around.ieuri.auth.application.member.required.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.member.required.PasswordEncoderPort
import org.around.ieuri.auth.application.member.required.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.token.provided.AuthTokenUseCase
import org.around.ieuri.auth.application.token.provided.dto.LoginCommand
import org.around.ieuri.auth.application.token.provided.dto.LoginEmailCommand
import org.around.ieuri.auth.application.token.provided.dto.LogoutCommand
import org.around.ieuri.auth.application.token.provided.dto.LogoutResult
import org.around.ieuri.auth.application.token.provided.dto.RefreshTokenCommand
import org.around.ieuri.auth.application.token.provided.dto.TokenPairResult
import org.around.ieuri.auth.application.token.provided.exception.InvalidTokenException
import org.around.ieuri.auth.application.token.provided.exception.TokenReuseDetectedException
import org.around.ieuri.auth.application.token.required.RefreshTokenStorePort
import org.around.ieuri.auth.application.token.required.TokenProviderPort
import org.around.ieuri.auth.domain.member.Email
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.ProviderUserId
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/** 인증 토큰 서비스: 로그인·refresh 회전·로그아웃. 발급은 [TokenIssuer]에 위임한다. */
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

    /** SNS 로그인. 미가입·정지는 거부하되, 기간제 정지가 만료됐으면 자동 복구 후 허용한다. */
    @Transactional
    override fun loginBySns(command: LoginCommand): TokenPairResult {
        val now = clock.instant()
        val identity = snsVerifier.verify(command.provider, command.idToken)
        val providerUserId = ProviderUserId(identity.providerUserId)

        val authentication = memberRepository.findAuthentication(command.provider, providerUserId)
            ?: throw MemberNotFoundException("가입되지 않은 계정입니다. 회원가입이 필요합니다.")

        ensureLoginable(
            authentication.memberId,
            authentication.status,
            authentication.suspendedUntil,
            authentication.suspensionReason,
            now,
        )

        // 탈퇴 시 연결 해제용 토큰 보충. 실패해도 로그인은 성공한다(R7).
        providerTokenCollector.collect(authentication.memberId, command.provider, command.authorizationCode)

        publishAccess(authentication.memberId, AccessType.LOGIN, command.ipAddress)
        return tokenIssuer.issueFor(authentication.memberId)
    }

    /** 자체(EMAIL) 로그인. 계정 존재 여부를 드러내지 않도록 실패는 InvalidCredentials로 통일한다. */
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
        // 이메일 미인증은 여기서 막지 않는다(SPEC-0024 R8). 차단은 오퍼레이션 게이트가 맡는다(R9).
        ensureLoginable(auth.memberId, auth.status, auth.suspendedUntil, auth.suspensionReason, now)

        publishAccess(auth.memberId, AccessType.LOGIN, command.ipAddress)
        return tokenIssuer.issueFor(auth.memberId)
    }

    /** 로그인 가능 상태 확인. 기간제 정지가 만료됐으면 자동 복구하고, 유효한 정지면 [InactiveMemberException]. */
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
     * refresh 회전. 이미 회전·폐기된 refresh 재사용은 탈취로 보고 회원 전체를 폐기한다.
     * - 신 토큰 저장이 구 토큰 폐기보다 먼저여야 한다(SPEC-0032 R5). Valkey는 @Transactional로 되돌릴 수 없어,
     *   순서를 바꾸면 중간 실패 뒤 재시도가 탈취로 오판되어 전 기기가 로그아웃된다.
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

    /** 로그아웃. 제출된 refresh의 jti를 폐기하며, 이미 무효/만료된 토큰이어도 성공한다(멱등). */
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

    /** 접속기록 발행(안전성 확보조치 기준 제8조). 성공한 접속만 남기며 저장은 리스너가 비동기로 한다. */
    private fun publishAccess(memberId: Long, accessType: AccessType, ipAddress: String?) {
        eventPublisher.publish(MemberAccessed(memberId, accessType, ipAddress))
    }
}
