package org.around.ieuri.common.observability

import io.micrometer.observation.ObservationPredicate
import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.observation.GraphQlObservationInstrumentation
import org.springframework.http.server.observation.ServerRequestObservationContext

/**
 * 관측에서 actuator 요청을 걷어낸다 — 메트릭(`http_server_requests`)과 트레이스 양쪽에 적용된다.
 */
@Configuration
class ObservabilityConfig {

    @Bean
    fun actuatorObservationPredicate(
        // ManagementServerProperties 빈을 주입받지 않는다 — 관리 포트를 분리하지 않아 빈이 없고, 주입하면 부팅이 실패한다.
        @Value("\${management.endpoints.web.base-path:/actuator}") basePath: String,
    ): ObservationPredicate = ObservationPredicate { name, context ->
        if (name == "http.server.requests" && context is ServerRequestObservationContext) {
            // carrier를 얻지 못하면 관측을 남긴다.
            context.carrier?.requestURI?.startsWith(basePath) != true
        } else {
            true
        }
    }

    /**
     * GraphQL 관측(`graphql.request`·`graphql.datafetcher`)을 켠다.
     * - 자동구성이 아니다 — 빼면 어떤 쿼리인지 span에서 구분되지 않는다.
     */
    @Bean
    fun graphQlObservationInstrumentation(
        observationRegistry: ObservationRegistry,
    ): GraphQlObservationInstrumentation = GraphQlObservationInstrumentation(observationRegistry)
}
