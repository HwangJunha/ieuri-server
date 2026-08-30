package org.around.ieuri.common.observability

import io.micrometer.observation.ObservationPredicate
import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.observation.GraphQlObservationInstrumentation
import org.springframework.http.server.observation.ServerRequestObservationContext

/**
 * 관측에서 actuator 요청을 걷어낸다.
 *
 * **우리가 만든 트래픽이라 진단 가치가 없는데 양은 가장 많다.** liveness·readiness 프로브가
 * 5초마다, 수집기의 `/actuator/prometheus` 스크레이프가 60초마다 들어오므로, 걸러내지 않으면
 * 트레이스 목록이 사실상 전부 actuator가 된다. 개발은 표본이 전량(1.0)이라 특히 그렇다.
 *
 * 실제 사용자 요청을 찾으려면 그 사이를 헤집어야 하고, 저장 용량도 이쪽으로 나간다.
 *
 * Spring Boot에 이를 끄는 설정 속성이 없어 빈으로 건다(spring-boot#34801). 판정은 관측
 * 단계에서 이뤄지므로 **메트릭과 트레이스 양쪽에 함께 적용된다** — `http_server_requests`
 * 메트릭에서도 actuator가 빠진다. 프로브의 성패는 k8s가 직접 보고 있으므로 잃는 것이 없다.
 */
@Configuration
class ObservabilityConfig {

    @Bean
    fun actuatorObservationPredicate(
        // **ManagementServerProperties 빈을 주입받지 않는다.** 그 빈은 관리 포트를 앱 포트와
        // 분리했을 때만 등록되는데 우리는 8080을 함께 쓰므로 존재하지 않는다 — 주입하면
        // `No qualifying bean of type ...ManagementServerProperties` 로 부팅이 실패한다.
        // 속성은 그와 무관하게 읽히므로 값으로 받는다.
        @Value("\${management.endpoints.web.base-path:/actuator}") basePath: String,
    ): ObservationPredicate {
        return ObservationPredicate { name, context ->
            if (name == "http.server.requests" && context is ServerRequestObservationContext) {
                // carrier 는 상위 타입(ReceiverContext)의 제네릭이라 Kotlin 에서 nullable 로
                // 보인다. 값을 얻지 못하면 판단할 근거가 없으므로 남기는 쪽을 택한다 —
                // 관측을 잃는 것보다 actuator 가 섞이는 편이 덜 나쁘다.
                context.carrier?.requestURI?.startsWith(basePath) != true
            } else {
                true
            }
        }
    }

    /**
     * GraphQL 관측을 켠다.
     *
     * **자동구성이 아니다.** Spring GraphQL 은 이 인스트루먼테이션을 애플리케이션이 직접
     * 등록하도록 하고 있어, 없으면 `graphql.request`·`graphql.datafetcher` 관측이 아예
     * 만들어지지 않는다.
     *
     * 없을 때 무엇이 보이지 않는가: 모든 GraphQL 요청은 `POST /graphql` 한 주소로 오므로
     * HTTP 계층 span 만으로는 **어떤 쿼리인지 구분되지 않는다.** 느린 요청을 찾아도 그것이
     * 어느 쿼리였는지 알 수 없어, 정작 고쳐야 할 곳을 짚지 못한다.
     *
     * datafetcher 관측은 "non-trivial" 한 데이터 페칭에만 붙는다 — 자바 객체의 필드를 그냥
     * 읽는 수준은 제외되므로 span 이 무한정 늘어나지 않는다.
     */
    @Bean
    fun graphQlObservationInstrumentation(
        observationRegistry: ObservationRegistry,
    ): GraphQlObservationInstrumentation = GraphQlObservationInstrumentation(observationRegistry)
}
