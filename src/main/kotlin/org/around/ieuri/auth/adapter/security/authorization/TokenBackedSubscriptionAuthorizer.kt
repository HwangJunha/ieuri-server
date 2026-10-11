package org.around.ieuri.auth.adapter.security.authorization
import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.auth.adapter.config.JwtProperties
import org.around.ieuri.auth.adapter.security.authentication.GraphqlWebSocketAuthInterceptor
import org.around.ieuri.auth.application.token.required.RefreshTokenStorePort
import org.around.ieuri.auth.domain.token.SocketIdentity
import org.around.ieuri.auth.domain.token.spec.LiveSubscriptionIdentity
import org.around.ieuri.common.security.SubscriptionAuthorizer
import org.around.ieuri.common.security.SubscriptionUnauthenticatedException
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/** 구독 인가의 auth 쪽 구현(SPEC-0032 R7). 연결 시각과 토큰 폐기 여부를 모아 [LiveSubscriptionIdentity]에 넘긴다. */
@Component
class TokenBackedSubscriptionAuthorizer(
    private val refreshTokenStore: RefreshTokenStorePort,
    private val jwtProperties: JwtProperties,
    private val clock: Clock,
) : SubscriptionAuthorizer {

    override fun requireLiveIdentity(dfe: DataFetchingEnvironment, memberId: Long) {
        // 연결 시각이 없으면 WebSocket이 아니다 — HTTP는 요청마다 JWT 필터가 판정한다.
        val connectedAt = dfe.graphQlContext.getOrDefault<Instant?>(
            GraphqlWebSocketAuthInterceptor.CONNECTED_AT,
            null,
        ) ?: return

        val identity = SocketIdentity(connectedAt, hasActiveToken = refreshTokenStore.hasActiveToken(memberId))

        if (!LiveSubscriptionIdentity(jwtProperties.accessTtl, clock.instant()).isSatisfiedBy(identity)) {
            throw SubscriptionUnauthenticatedException(
                "구독 신원이 만료되었습니다. 토큰을 갱신하고 다시 연결해 주세요.",
            )
        }
    }
}
