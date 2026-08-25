package org.around.ieuri.auth.infrastructure.security

import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.domain.specification.SubscriptionIdentity
import org.around.ieuri.auth.infrastructure.config.JwtProperties
import org.around.ieuri.common.security.SubscriptionAuthorizer
import org.around.ieuri.common.security.SubscriptionUnauthenticatedException
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * 구독 인가의 auth 쪽 구현(SPEC-0032 R7).
 *
 * 두 가지를 본다 — **연결이 액세스 TTL을 넘겼는지**, 그리고 **그 회원의 토큰이 전부
 * 폐기됐는지**. 폐기는 탈퇴·정지·재사용 감지가 이미 `revokeAllByMember`를 부르므로
 * 저장소를 읽으면 알 수 있다.
 *
 * 판정 자체는 순수 함수([SubscriptionIdentity])에 두고 여기서는 값을 모아 넘긴다.
 */
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

        val allowed = SubscriptionIdentity.canSubscribe(
            connectedAt = connectedAt,
            now = clock.instant(),
            accessTtl = jwtProperties.accessTtl,
            hasActiveToken = refreshTokenStore.hasActiveToken(memberId),
        )

        if (!allowed) {
            throw SubscriptionUnauthenticatedException(
                "구독 신원이 만료되었습니다. 토큰을 갱신하고 다시 연결해 주세요.",
            )
        }
    }
}
