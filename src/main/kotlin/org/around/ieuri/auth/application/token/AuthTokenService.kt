package org.around.ieuri.auth.application.token

import org.around.ieuri.auth.application.member.provided.MemberLoginUseCase
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
import org.around.ieuri.auth.application.token.required.TokenEventPublisherPort
import org.around.ieuri.auth.application.token.required.TokenProviderPort
import org.around.ieuri.common.event.AccessType
import org.around.ieuri.common.event.MemberAccessed
import org.springframework.stereotype.Service

/**
 * 로그인·refresh 회전·로그아웃. 트랜잭션을 갖지 않는다 — 회원 인증이 커밋된 뒤 토큰을 발급한다.
 * - 발급이 실패하면 인증 단계의 변경(만료된 정지의 복구)은 남는다. 다시 로그인하면 된다.
 */
@Service
class AuthTokenService(
    private val memberLogin: MemberLoginUseCase,
    private val tokenProvider: TokenProviderPort,
    private val refreshTokenStore: RefreshTokenStorePort,
    private val tokenIssuer: TokenIssuer,
    private val eventPublisher: TokenEventPublisherPort,
) : AuthTokenUseCase {

    /** 미가입·정지는 거부하되, 기간제 정지가 만료됐으면 복구 후 허용한다. */
    override fun loginBySns(command: LoginCommand): TokenPairResult {
        val memberId = memberLogin.loginBySns(command.provider, command.idToken, command.authorizationCode)
        publishAccess(memberId, AccessType.LOGIN, command.ipAddress)
        return tokenIssuer.issueFor(memberId)
    }

    override fun loginByEmail(command: LoginEmailCommand): TokenPairResult {
        val memberId = memberLogin.loginByEmail(command.email, command.password)
        publishAccess(memberId, AccessType.LOGIN, command.ipAddress)
        return tokenIssuer.issueFor(memberId)
    }

    /**
     * refresh 회전. 이미 회전·폐기된 refresh를 다시 내면 탈취로 보고 회원의 세션을 모두 끊는다.
     * - 새 토큰 저장이 옛 토큰 폐기보다 먼저여야 한다(SPEC-0032 R5). 순서를 바꾸면 중간 실패 뒤 재시도가
     *   탈취로 오판되어 전 기기가 로그아웃된다.
     */
    override fun refresh(command: RefreshTokenCommand): TokenPairResult {
        // 서명·만료·타입이 틀리면 InvalidTokenException.
        val claims = tokenProvider.parseRefreshToken(command.refreshToken)

        if (!refreshTokenStore.existsActive(claims.jti)) {
            refreshTokenStore.revokeAllByMember(claims.memberId)
            throw TokenReuseDetectedException("재사용된 리프레시 토큰입니다. 다시 로그인해 주세요.")
        }

        val issued = tokenIssuer.issueFor(claims.memberId)
        refreshTokenStore.revoke(claims.jti)
        return issued
    }

    /** 제출된 refresh를 폐기한다. 이미 무효·만료된 토큰이어도 성공한다(멱등). */
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

    /** 접속기록(안전성 확보조치 기준 제8조). 성공한 접속만 남기며 저장은 리스너가 비동기로 한다. */
    private fun publishAccess(memberId: Long, accessType: AccessType, ipAddress: String?) {
        eventPublisher.publish(MemberAccessed(memberId, accessType, ipAddress))
    }
}
