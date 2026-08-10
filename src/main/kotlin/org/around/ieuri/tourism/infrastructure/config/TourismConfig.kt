package org.around.ieuri.tourism.infrastructure.config

import org.around.ieuri.tourism.application.policy.MapViewportPolicy
import org.around.ieuri.tourism.application.policy.SeoulCitydataPolicy
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.infrastructure.batch.FestivalSyncProperties
import org.around.ieuri.tourism.infrastructure.batch.SpotSyncProperties
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
@EnableConfigurationProperties(
    TourApiProperties::class,
    SpotSyncProperties::class,
    FestivalSyncProperties::class,
    SeoulCitydataProperties::class,
    MapViewportProperties::class,
)
class TourismConfig {

    @Bean
    fun spotQueryPolicy(properties: TourApiProperties): SpotQueryPolicy =
        SpotQueryPolicy(
            maxRadiusMeters = properties.maxRadiusMeters,
            maxPageSize = properties.maxPageSize,
            maxLikedPageSize = properties.maxLikedPageSize,
            detailAbsenceTtl = properties.detailAbsenceTtl,
            maxSearchPageSize = properties.maxSearchPageSize,
            maxSuggestionCount = properties.maxSuggestionCount,
            maxSearchQueryLength = properties.maxSearchQueryLength,
            maxSearchPage = properties.maxSearchPage,
            minPopularLikeCount = properties.minPopularLikeCount,
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

    @Bean
    fun mapViewportPolicy(properties: MapViewportProperties): MapViewportPolicy =
        MapViewportPolicy(
            minZoom = properties.minZoom,
            maxZoom = properties.maxZoom,
            maxAreaInSquareDegrees = properties.maxAreaSquareDegrees,
            maxSpotMarkers = properties.maxSpotMarkers,
            maxEventMarkers = properties.maxEventMarkers,
        )

    @Bean
    fun seoulCitydataPolicy(properties: SeoulCitydataProperties): SeoulCitydataPolicy =
        SeoulCitydataPolicy(
            minRefetchInterval = properties.minRefetchInterval,
            maxAreaIds = properties.maxAreaIds,
        )

    /**
     * 서울 실시간 도시데이터 호출용 RestClient. TourAPI 쪽보다 타임아웃을 짧게 잡는다 —
     * 이 호출이 실패해도 degraded로 흡수되므로(R11), 오래 매달리는 것보다 빨리 포기하고
     * 캐시를 주는 편이 낫다.
     */
    @Bean
    fun seoulCitydataRestClient(
        builder: RestClient.Builder,
        properties: SeoulCitydataProperties,
    ): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis))
            setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis))
        }
        return builder.requestFactory(requestFactory).build()
    }
}
