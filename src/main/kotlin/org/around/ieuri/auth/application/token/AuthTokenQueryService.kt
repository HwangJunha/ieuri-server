package org.around.ieuri.auth.application.token

import org.around.ieuri.auth.application.token.provided.AuthTokenQueryUseCase
import org.around.ieuri.auth.application.token.provided.dto.AccessTokenClaims
import org.around.ieuri.auth.application.token.required.RefreshTokenStorePort
import org.around.ieuri.auth.application.token.required.TokenProviderPort
import org.around.ieuri.auth.domain.token.SocketIdentity
import org.around.ieuri.auth.domain.token.spec.LiveSubscriptionIdentity
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/** 요청의 신원 확인. 요청마다 도는 경로라 트랜잭션을 걸지 않는다 — DB를 읽지 않는다. */
@Service
class AuthTokenQueryService(
    private val tokenProvider: TokenProviderPort,
    private val refreshTokenStore: RefreshTokenStorePort,
    private val liveSubscriptionIdentity: LiveSubscriptionIdentity,
    private val clock: Clock,
) : AuthTokenQueryUseCase {

    override fun authenticate(accessToken: String): AccessTokenClaims = tokenProvider.parseAccessToken(accessToken)

    override fun canOpenSubscription(memberId: Long, connectedAt: Instant): Boolean = liveSubscriptionIdentity.isSatisfiedBy(
        SocketIdentity(connectedAt, hasActiveToken = refreshTokenStore.hasActiveToken(memberId), checkedAt = clock.instant()),
    )
}
