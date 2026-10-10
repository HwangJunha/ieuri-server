package org.around.ieuri.auth.application.token

import org.around.ieuri.auth.application.member.provided.MemberQueryUseCase
import org.around.ieuri.auth.application.token.provided.dto.TokenPairResult
import org.around.ieuri.auth.application.token.required.RefreshTokenStorePort
import org.around.ieuri.auth.application.token.required.TokenProviderPort
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/** access/refresh 쌍을 발급하고 refresh jti를 저장소에 등록한다. 발급마다 유효 권한을 다시 읽는다. */
@Component
class TokenIssuer(
    private val tokenProvider: TokenProviderPort,
    private val refreshTokenStore: RefreshTokenStorePort,
    private val memberQuery: MemberQueryUseCase,
    private val clock: Clock,
) {
    fun issueFor(memberId: Long): TokenPairResult {
        val authorities = memberQuery.authoritiesOf(memberId)

        val access = tokenProvider.issueAccessToken(memberId, authorities.permissions, authorities.roles)
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
