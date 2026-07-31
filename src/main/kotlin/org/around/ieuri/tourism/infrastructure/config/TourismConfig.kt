package org.around.ieuri.tourism.infrastructure.config

import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * tourism 도메인 인프라 빈 구성. 설정값(TourApiProperties)에서 application 정책(SpotQueryPolicy)을 뽑고,
 * TourAPI 호출용 RestClient를 빈으로 등록한다. @ConfigurationPropertiesScan이 없으므로 여기서 명시적으로
 * @EnableConfigurationProperties 한다(프로젝트 관례).
 */
@Configuration
@EnableConfigurationProperties(TourApiProperties::class)
class TourismConfig {

    @Bean
    fun spotQueryPolicy(properties: TourApiProperties): SpotQueryPolicy =
        SpotQueryPolicy(
            maxRadiusMeters = properties.maxRadiusMeters,
            maxPageSize = properties.maxPageSize,
        )

    /**
     * TourAPI 호출용 RestClient. Boot가 자동구성한 [RestClient.Builder](메시지 컨버터 등 포함)에 타임아웃만 입혀
     * build한다. 타임아웃은 필수: upstream 스톨이 요청 스레드를 무한정 매달지 않고 UPSTREAM_UNAVAILABLE로 전환되도록.
     * base-url은 언어별로 달라 여기서 고정하지 않고 어댑터가 매 호출 절대 URI로 넘긴다.
     */
    @Bean
    fun tourApiRestClient(builder: RestClient.Builder, properties: TourApiProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis))
            setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis))
        }
        return builder.requestFactory(requestFactory).build()
    }
}
