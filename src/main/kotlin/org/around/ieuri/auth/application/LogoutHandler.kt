package org.around.ieuri.auth.application

import org.around.ieuri.auth.application.command.LogoutCommand
import org.around.ieuri.auth.application.exception.InvalidTokenException
import org.around.ieuri.auth.application.port.`in`.command.LogoutResult
import org.around.ieuri.auth.application.port.`in`.command.LogoutUseCase
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
import org.springframework.stereotype.Service

/**
 * 로그아웃. 제출된 refresh의 jti를 저장소에서 폐기한다.
 * 이미 무효/만료된 토큰이어도 멱등하게 성공 처리한다(클라이언트는 토큰을 폐기하면 됨).
 * accessToken은 짧은 만료로 자연 소멸한다(즉시 무효화는 범위 밖).
 */
@Service
class LogoutHandler(
    private val tokenProvider: TokenProviderPort,
    private val refreshTokenStore: RefreshTokenStorePort,
) : LogoutUseCase {

    override fun handle(command: LogoutCommand): LogoutResult {
        val claims = try {
            tokenProvider.parseRefreshToken(command.refreshToken)
        } catch (ex: InvalidTokenException) {
            return LogoutResult(success = true)
        }
        refreshTokenStore.revoke(claims.jti)
        return LogoutResult(success = true)
    }
}
