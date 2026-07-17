package org.around.ieuri.auth.application

import org.around.ieuri.auth.application.port.`in`.command.TokenPairResult
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * accessToken/refreshToken 쌍을 발급하고 refresh jti를 저장소에 등록하는 공통 로직.
 * 로그인·재발급(회전) 양쪽에서 재사용한다.
 */
@Component
class TokenIssuer(
    private val tokenProvider: TokenProviderPort,
    private val refreshTokenStore: RefreshTokenStorePort,
    private val clock: Clock,
) {
    fun issueFor(memberId: Long): TokenPairResult {
        val access = tokenProvider.issueAccessToken(memberId)
        val refresh = tokenProvider.issueRefreshToken(memberId)
        refreshTokenStore.save(refresh.jti, memberId, refresh.expiresAt)

        val now = clock.instant()
        return TokenPairResult(
            accessToken = access.token,
            refreshToken = refresh.token,
            tokenType = "Bearer",
            accessTokenExpiresInSeconds = Duration.between(now, access.expiresAt).seconds,
            refreshTokenExpiresInSeconds = Duration.between(now, refresh.expiresAt).seconds,
        )
    }
}
