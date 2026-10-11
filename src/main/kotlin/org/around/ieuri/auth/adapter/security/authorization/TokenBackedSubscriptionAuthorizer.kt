package org.around.ieuri.auth.adapter.security.authorization
import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.auth.adapter.security.authentication.GraphqlWebSocketAuthInterceptor
import org.around.ieuri.auth.application.token.provided.AuthTokenQueryUseCase
import org.around.ieuri.common.security.SubscriptionAuthorizer
import org.around.ieuri.common.security.SubscriptionUnauthenticatedException
import org.springframework.stereotype.Component
import java.time.Instant

/** 구독 인가의 auth 쪽 구현(SPEC-0032 R7). 소켓의 연결 시각을 꺼내 유스케이스에 판정을 맡긴다. */
@Component
class TokenBackedSubscriptionAuthorizer(
    private val authTokenQueryUseCase: AuthTokenQueryUseCase,
) : SubscriptionAuthorizer {

    override fun requireLiveIdentity(dfe: DataFetchingEnvironment, memberId: Long) {
        // 연결 시각이 없으면 WebSocket이 아니다 — HTTP는 요청마다 JWT 필터가 판정한다.
        val connectedAt = dfe.graphQlContext.getOrDefault<Instant?>(
            GraphqlWebSocketAuthInterceptor.CONNECTED_AT,
            null,
        ) ?: return

        if (!authTokenQueryUseCase.canOpenSubscription(memberId, connectedAt)) {
            throw SubscriptionUnauthenticatedException(
                "구독 신원이 만료되었습니다. 토큰을 갱신하고 다시 연결해 주세요.",
            )
        }
    }
}
