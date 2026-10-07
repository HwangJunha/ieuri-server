package org.around.ieuri.tourism.adapter.config

import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout
import org.around.ieuri.tourism.adapter.batch.FestivalSyncProperties
import org.around.ieuri.tourism.adapter.batch.RegionMetricSyncProperties
import org.around.ieuri.tourism.adapter.batch.SpotSyncProperties
import org.around.ieuri.tourism.application.catalog.MapViewportPolicy
import org.around.ieuri.tourism.application.catalog.SpotQueryPolicy
import org.around.ieuri.tourism.application.congestion.SeoulOpenDataPolicy
import org.around.ieuri.tourism.application.footprint.FootprintPolicy
import org.around.ieuri.tourism.application.recommendation.EmbeddingSpec
import org.around.ieuri.tourism.application.recommendation.MapRecommendationPolicy
import org.around.ieuri.tourism.application.recommendation.RecommendationPolicy
import org.around.ieuri.tourism.application.region.RegionMetricSyncPolicy
import org.around.ieuri.tourism.application.region.RegionRecommendationPolicy
import org.around.ieuri.tourism.application.relation.MapRelationPolicy
import org.around.ieuri.tourism.domain.recommendation.CategoryDiversity
import org.around.ieuri.tourism.domain.recommendation.RecommendationScore
import org.around.ieuri.tourism.domain.recommendation.TasteThreshold
import org.around.ieuri.tourism.domain.region.DistanceScoring
import org.around.ieuri.tourism.domain.region.FreshnessDecay
import org.around.ieuri.tourism.domain.region.ProvinceEligibility
import org.around.ieuri.tourism.domain.region.RegionScoreWeights
import org.around.ieuri.tourism.domain.spot.SpotExclusionRule
import org.around.ieuri.tourism.domain.spot.TaxRefundTitleRule
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.time.Duration

/**
 * tourism 도메인 인프라 빈 구성 — 설정값에서 application 정책을 뽑고 외부 호출용 RestClient를 등록한다.
 */
@Configuration
@EnableConfigurationProperties(
    TourApiProperties::class,
    SpotSyncProperties::class,
    FestivalSyncProperties::class,
    SeoulOpenDataProperties::class,
    MapViewportProperties::class,
    KtoBigDataProperties::class,
    RegionMetricSyncProperties::class,
    DistrictRepresentativeProperties::class,
    SpotExclusionProperties::class,
    NationwideMapProperties::class,
    RecommendationProperties::class,
    VoyageProperties::class,
)
class TourismConfig {

    /** 추천 정책(SPEC-0035). 도메인 순수 함수에 설정값을 꽂아 조립한다. */
    @Bean
    fun recommendationPolicy(properties: RecommendationProperties): RecommendationPolicy = RecommendationPolicy(
        score = RecommendationScore(
            distanceHalfLifeMeters = properties.distanceHalfLifeMeters,
            taxRefundMultiplier = properties.taxRefundMultiplier,
            taxRefundTitleRule = TaxRefundTitleRule(properties.taxRefundTitlePatterns),
            tasteWeight = properties.tasteWeight,
            likedMultiplier = properties.likedMultiplier,
            dislikedMultiplier = properties.dislikedMultiplier,
        ),
        diversity = CategoryDiversity(properties.categoryRepeatPenalty),
        candidateCap = properties.candidateCap,
        tasteThreshold = TasteThreshold(properties.tasteMinReactions),
        embedding = EmbeddingSpec(
            model = properties.embedding.model,
            dimension = properties.embedding.dimension,
            retryCount = properties.embedding.retryCount,
            overviewMaxChars = properties.embedding.overviewMaxChars,
        ),
    )

    /** Voyage 임베딩 호출용 RestClient(SPEC-0035). 배치 경로라 읽기 타임아웃을 길게 잡는다. */
    @Bean
    fun voyageRestClient(builder: RestClient.Builder, properties: VoyageProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis))
            setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis))
        }
        return builder.requestFactory(requestFactory).build()
    }

    /** 비관광 spot 제외 규칙(SPEC-0018). 키워드는 설정에서 온다. */
    @Bean
    fun spotExclusionRule(properties: SpotExclusionProperties): SpotExclusionRule = SpotExclusionRule(
        keywords = properties.keywords,
        branchNamePatterns = properties.branchNamePatterns,
    )

    @Bean
    fun spotQueryPolicy(properties: TourApiProperties): SpotQueryPolicy = SpotQueryPolicy(
        maxRadiusMeters = properties.maxRadiusMeters,
        maxPageSize = properties.maxPageSize,
        maxLikedPageSize = properties.maxLikedPageSize,
        maxSearchPageSize = properties.maxSearchPageSize,
        maxSuggestionCount = properties.maxSuggestionCount,
        maxSearchQueryLength = properties.maxSearchQueryLength,
        maxSearchPage = properties.maxSearchPage,
        minPopularLikeCount = properties.minPopularLikeCount,
    )

    /**
     * TourAPI 호출용 RestClient. 풀링된 커넥션을 재사용한다.
     * - 풀 없이 수만 회를 연속 호출하면 로컬 포트가 잠식되고 upstream 유량 제어(429)에 걸린다.
     * - base-url은 언어별로 달라 어댑터가 매 호출 절대 URI로 넘긴다.
     */
    @Bean
    fun tourApiRestClient(builder: RestClient.Builder, properties: TourApiProperties): RestClient {
        val connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            // 언어별 호스트가 넷이라 라우트도 넷이다. 기본값(라우트당 5)이면 한 언어의 동시 호출이 줄을 선다.
            .setMaxConnPerRoute(64)
            .setMaxConnTotal(256)
            .setDefaultConnectionConfig(
                ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.ofMilliseconds(properties.connectTimeoutMillis))
                    .setSocketTimeout(Timeout.ofMilliseconds(properties.readTimeoutMillis))
                    // 서버가 이미 닫은 유휴 연결을 쓰기 전에 확인한다(`NoHttpResponseException` 방지).
                    .setValidateAfterInactivity(Timeout.ofSeconds(5))
                    .build(),
            )
            .build()

        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            // 유휴 연결을 정리해 포트를 붙잡고 있지 않게 한다.
            .evictIdleConnections(TimeValue.ofSeconds(30))
            .build()

        // 타임아웃은 위 ConnectionConfig가 갖는다.
        return builder.requestFactory(HttpComponentsClientHttpRequestFactory(httpClient)).build()
    }

    @Bean
    fun footprintPolicy(properties: NationwideMapProperties): FootprintPolicy = FootprintPolicy(
        maxCellSpotPageSize = properties.maxCellSpotPageSize,
        shareUrlBase = properties.shareUrlBase,
    )

    @Bean
    fun mapRelationPolicy(properties: NationwideMapProperties): MapRelationPolicy = MapRelationPolicy(maxPageSize = properties.maxRelationPageSize)

    @Bean
    fun mapViewportPolicy(properties: MapViewportProperties): MapViewportPolicy = MapViewportPolicy(
        minZoom = properties.minZoom,
        maxZoom = properties.maxZoom,
        maxAreaInSquareDegrees = properties.maxAreaSquareDegrees,
        maxSpotMarkers = properties.maxSpotMarkers,
        maxEventMarkers = properties.maxEventMarkers,
    )

    /** 지도 영역 추천 정책. 뷰포트와 같은 설정 묶음(`map-viewport`)에서 읽는다 — 설정 키는 바꾸지 않는다. */
    @Bean
    fun mapRecommendationPolicy(properties: MapViewportProperties): MapRecommendationPolicy = MapRecommendationPolicy(
        maxAreaInSquareDegrees = properties.maxAreaSquareDegrees,
        candidateCap = properties.recommendationCandidateCap,
        maxSize = properties.maxRecommendationSize,
    )

    @Bean
    fun regionMetricSyncPolicy(properties: KtoBigDataProperties, syncProperties: RegionMetricSyncProperties): RegionMetricSyncPolicy = RegionMetricSyncPolicy(
        pageSize = properties.pageSize,
        visitorBackfillDays = syncProperties.visitorBackfillDays,
        monthlyLookbackMonths = syncProperties.monthlyLookbackMonths,
    )

    @Bean
    fun seoulOpenDataPolicy(properties: SeoulOpenDataProperties): SeoulOpenDataPolicy = SeoulOpenDataPolicy(
        minRefetchInterval = properties.minRefetchInterval,
        maxAreaIds = properties.maxAreaIds,
    )

    /**
     * 지역 추천 정책(SPEC-0015 §3.3). 계수는 설정에서 온 잠정값이다(POL-044).
     * - `scoreWeights`가 비어 있으면 균등 가중이다.
     */
    @Bean
    fun regionRecommendationPolicy(properties: KtoBigDataProperties): RegionRecommendationPolicy = RegionRecommendationPolicy(
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
        provinceEligibility = ProvinceEligibility(properties.minDistrictsPerProvince),
        defaultLimit = properties.defaultLimit,
        maxLimit = properties.maxLimit,
    )

    /**
     * 관광통계 호출용 RestClient(SPEC-0015). 배치 경로라 읽기 타임아웃을 길게 잡는다.
     * - `ACCEPT_EMPTY_STRING_AS_NULL_OBJECT`가 필수다 — 결과 없는 날짜는 `"items": ""`로 오며,
     *   없으면 부재가 UPSTREAM_UNAVAILABLE로 둔갑한다(R8).
     */
    @Bean
    fun ktoBigDataRestClient(builder: RestClient.Builder, properties: KtoBigDataProperties): RestClient {
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
            // 기본 JSON 컨버터를 위 mapper로 갈아 끼운다.
            .configureMessageConverters { it.withJsonConverter(JacksonJsonHttpMessageConverter(mapper)) }
            .build()
    }

    /** 서울 실시간 도시데이터 호출용 RestClient. 실패는 degraded로 흡수되므로(R11) 타임아웃을 짧게 잡는다. */
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
