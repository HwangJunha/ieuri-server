package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.command.LoginCommand
import org.around.ieuri.auth.application.command.LoginEmailCommand
import org.around.ieuri.auth.application.command.LogoutCommand
import org.around.ieuri.auth.application.command.RefreshTokenCommand
import org.around.ieuri.auth.application.exception.EmailNotVerifiedException
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
import org.around.ieuri.auth.application.port.out.security.PasswordEncoderPort
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.ProviderUserId
import org.springframework.stereotype.Service
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
        return tokenIssuer.issueFor(authentication.memberId)
    }

    /**
     * 자체(EMAIL) 로그인. 이메일로 회원을 찾아 비밀번호를 검증하고, 이메일 인증 완료·상태를 확인한 뒤 토큰을 발급한다.
     * 계정 존재 여부를 드러내지 않도록 실패 메시지는 통일한다(InvalidCredentials).
     */
    @Transactional
    override fun loginByEmail(command: LoginEmailCommand): TokenPairResult {
        val now = clock.instant()
        val email = Email(command.email).value

        val auth = memberRepository.findEmailAuthentication(email)
            ?: throw InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.")
        // passwordHash가 없으면 SNS 계정 → 이메일 로그인 불가(존재 여부는 드러내지 않음).
        val passwordHash = auth.passwordHash
            ?: throw InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.")
        if (!passwordEncoder.matches(command.password, passwordHash)) {
            throw InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.")
        }
        if (!auth.emailVerified) {
            throw EmailNotVerifiedException("이메일 인증이 필요합니다. 메일함의 인증 코드를 확인해 주세요.")
        }

        ensureLoginable(auth.memberId, auth.status, auth.suspendedUntil, auth.suspensionReason, now)
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
     */
    override fun refresh(command: RefreshTokenCommand): TokenPairResult {
        // 서명/만료/타입 검증 실패 시 InvalidTokenException.
        val claims = tokenProvider.parseRefreshToken(command.refreshToken)

        if (!refreshTokenStore.existsActive(claims.jti)) {
            // 재사용 감지: 이미 회전·폐기된 refresh → 해당 회원 전체 폐기.
            refreshTokenStore.revokeAllByMember(claims.memberId)
            throw TokenReuseDetectedException("재사용된 리프레시 토큰입니다. 다시 로그인해 주세요.")
        }

        // 회전: 이전 refresh 폐기 후 새 토큰 쌍 발급.
        refreshTokenStore.revoke(claims.jti)
        return tokenIssuer.issueFor(claims.memberId)
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
        return LogoutResult(success = true)
    }
}
