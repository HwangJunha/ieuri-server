package org.around.ieuri.tourism.infrastructure.config

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import org.around.ieuri.tourism.application.policy.MapViewportPolicy
import org.around.ieuri.tourism.application.policy.RegionRecommendationPolicy
import org.around.ieuri.tourism.application.policy.SeoulOpenDataPolicy
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.domain.vo.DistanceScoring
import org.around.ieuri.tourism.domain.vo.FreshnessDecay
import org.around.ieuri.tourism.domain.vo.RegionScoreWeights
import org.around.ieuri.tourism.infrastructure.batch.FestivalSyncProperties
import org.around.ieuri.tourism.infrastructure.batch.TourStatsSyncProperties
import org.around.ieuri.tourism.infrastructure.batch.SpotSyncProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
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
    SeoulOpenDataProperties::class,
    MapViewportProperties::class,
    TourStatsProperties::class,
    TourStatsSyncProperties::class,
    DistrictRepresentativeProperties::class,
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
    fun seoulOpenDataPolicy(properties: SeoulOpenDataProperties): SeoulOpenDataPolicy =
        SeoulOpenDataPolicy(
            minRefetchInterval = properties.minRefetchInterval,
            maxAreaIds = properties.maxAreaIds,
        )

    /**
     * 지역 추천 정책(SPEC-0015 §3.3). 계수는 전부 설정에서 온 잠정값이다(POL-044).
     *
     * `scoreWeights`가 비어 있으면 균등 가중이다 — 설정이 덜 채워진 상태를 "그 항목을 쓰지
     * 않겠다"로 해석하지 않는다.
     */
    @Bean
    fun regionRecommendationPolicy(properties: TourStatsProperties): RegionRecommendationPolicy =
        RegionRecommendationPolicy(
            weights = if (properties.scoreWeights.isEmpty()) {
                RegionScoreWeights.uniform()
            } else {
                RegionScoreWeights(properties.scoreWeights)
            },
            distanceScoring = DistanceScoring(properties.distanceHalfLifeMeters),
            freshnessDecay = FreshnessDecay(
                plateauDays = properties.freshnessPlateauDays,
                halfLifeDays = properties.freshnessHalfLifeDays,
            ),
            minFactorCount = properties.minFactorCount,
            defaultLimit = properties.defaultLimit,
            maxLimit = properties.maxLimit,
        )

    /**
     * 관광통계 호출용 RestClient(SPEC-0015). TourAPI 쪽과 분리한다 — 서비스 경로가 셋으로
     * 갈리고 배치 경로라 읽기 타임아웃을 길게 잡을 수 있다(요청 스레드를 매달지 않는다).
     *
     * base-url은 서비스별로 달라 여기서 고정하지 않고 어댑터가 매 호출 절대 URI로 넘긴다.
     *
     * **`ACCEPT_EMPTY_STRING_AS_NULL_OBJECT`가 필수다.** 결과가 없는 날짜를 요청하면 업스트림이
     * `"items": {...}`가 아니라 `"items": ""`를 준다(실측 2026-08-11). 이 설정이 없으면 그
     * 빈 문자열에서 역직렬화가 깨지고, 전송 계층 예외로 잡혀 **부재가 UPSTREAM_UNAVAILABLE
     * 장애로 둔갑한다** — R8이 금지하는 바로 그 동작이다.
     */
    @Bean
    fun ktoBigDataRestClient(builder: RestClient.Builder, properties: TourStatsProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis))
            setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis))
        }
        val mapper = JsonMapper.builder()
            .addModule(kotlinModule())
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .build()
        return builder
            .requestFactory(requestFactory)
            .messageConverters { it.add(0, JacksonJsonHttpMessageConverter(mapper)) }
            .build()
    }

    /**
     * 서울 실시간 도시데이터 호출용 RestClient. TourAPI 쪽보다 타임아웃을 짧게 잡는다 —
     * 이 호출이 실패해도 degraded로 흡수되므로(R11), 오래 매달리는 것보다 빨리 포기하고
     * 캐시를 주는 편이 낫다.
     */
    @Bean
    fun seoulOpenDataRestClient(
        builder: RestClient.Builder,
        properties: SeoulOpenDataProperties,
    ): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis))
            setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis))
        }
        return builder.requestFactory(requestFactory).build()
    }
}
