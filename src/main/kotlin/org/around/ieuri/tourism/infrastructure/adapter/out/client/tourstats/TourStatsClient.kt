package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourstats

import org.around.ieuri.tourism.application.command.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.command.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.command.SyncVisitorCountCommand
import org.around.ieuri.tourism.application.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.port.out.client.TourStatsSyncPort
import org.around.ieuri.tourism.application.port.out.client.TourStatsSyncPage
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourstats.dto.TourStatsResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourstats.dto.TourStatsConcentrationRateItem
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourstats.dto.TourStatsDemandIntensityItem
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourstats.dto.TourStatsVisitorCountItem
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourstats.mapper.toSyncCommandOrNull
import org.around.ieuri.tourism.infrastructure.config.TourStatsProperties
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * KTO 빅데이터 4종 어댑터(SPEC-0015 §5). [TourStatsSyncPort]의 유일한 구현체로, 업스트림
 * 세부(서비스 경로 · 필드명 · 오류 코드)를 여기 가둔다.
 *
 * **4개 오퍼레이션을 한 클래스에 둔다** — serviceKey · 응답 봉투 · 예외 변환 규칙이 전부 같아
 * 나누면 그 규칙이 네 벌로 복제된다. 서비스 경로만 갈리며 그것은 설정값이다.
 *
 * **예외를 변환한다 — 기존 [org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiClient]의
 * 결함을 물려받지 않는다**(§5 · R8). 변환 규칙은 [translate] 참조.
 *
 * serviceKey는 로그에 남기지 않는다. URI를 통째로 로깅하지 않는 것도 같은 이유다.
 */
@Component
class TourStatsClient(
    private val properties: TourStatsProperties,
    private val ktoBigDataRestClient: RestClient,
) : TourStatsSyncPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun fetchVisitorCounts(
        startDate: LocalDate,
        endDate: LocalDate,
        pageNo: Int,
        numOfRows: Int,
    ): TourStatsSyncPage<SyncVisitorCountCommand> {
        val uri = uriFor(TourStatsProperties.SERVICE_DATA_LAB, OP_VISITOR_COUNT, pageNo, numOfRows) {
            // 구간을 통째로 받는다 — 지역 필터 파라미터가 없어 날짜만 주면 전 지역이 온다(§4.3).
            queryParam("startYmd", startDate.format(DATE_FORMAT))
            queryParam("endYmd", endDate.format(DATE_FORMAT))
        }
        return fetchPage(uri, VISITOR_COUNT_TYPE) { it.toSyncCommandOrNull() }
    }

    override fun fetchConcentrationRates(
        baseDate: LocalDate,
        pageNo: Int,
        numOfRows: Int,
    ): TourStatsSyncPage<SyncConcentrationRateCommand> {
        val uri = uriFor(TourStatsProperties.SERVICE_TATS_CNCTR_RATE, OP_CONCENTRATION_RATE, pageNo, numOfRows) {
            // 이 오퍼레이션은 발표 기준일 하나를 받고 향후 30일 예측을 함께 돌려준다(R5) —
            // 대상일은 응답 행마다 다르며 우리가 날짜별로 나눠 부르지 않는다.
            queryParam("baseYmd", baseDate.format(DATE_FORMAT))
        }
        return fetchPage(uri, CONCENTRATION_RATE_TYPE) { it.toSyncCommandOrNull() }
    }

    override fun fetchStayIntensities(
        baseMonth: YearMonth,
        pageNo: Int,
        numOfRows: Int,
    ): TourStatsSyncPage<SyncDemandIntensityCommand> =
        fetchDemandIntensity(OP_STAY_INTENSITY, baseMonth, pageNo, numOfRows)

    override fun fetchSpendingIntensities(
        baseMonth: YearMonth,
        pageNo: Int,
        numOfRows: Int,
    ): TourStatsSyncPage<SyncDemandIntensityCommand> =
        fetchDemandIntensity(OP_SPENDING_INTENSITY, baseMonth, pageNo, numOfRows)

    /** 체류·소비 강도는 같은 서비스의 다른 오퍼레이션이며 응답 모양도 같다. */
    private fun fetchDemandIntensity(
        operation: String,
        baseMonth: YearMonth,
        pageNo: Int,
        numOfRows: Int,
    ): TourStatsSyncPage<SyncDemandIntensityCommand> {
        val uri = uriFor(TourStatsProperties.SERVICE_AREA_TAR_DEM_DS, operation, pageNo, numOfRows) {
            queryParam("startYm", baseMonth.format(MONTH_FORMAT))
            queryParam("endYm", baseMonth.format(MONTH_FORMAT))
        }
        return fetchPage(uri, DEMAND_INTENSITY_TYPE) { it.toSyncCommandOrNull() }
    }

    /**
     * 한 페이지를 받아 적재 명령으로 옮긴다. 매핑 실패 행은 드랍하고 건수만 센다 — 한 행의
     * 결손이 페이지 전체를 실패시키지 않는다.
     *
     * **빈 응답은 정상이다** — 빈 [TourStatsSyncPage]로 돌아가며 예외가 되지 않는다(R8 · §5).
     */
    private fun <I : Any, R : Any> fetchPage(
        uri: URI,
        type: ParameterizedTypeReference<TourStatsResponse<I>>,
        map: (I) -> R?,
    ): TourStatsSyncPage<R> {
        val response = call(uri, type)
        val items = response.items
        val records = items.mapNotNull(map)
        return TourStatsSyncPage(
            records = records,
            totalCount = response.totalCount,
            droppedCount = items.size - records.size,
        )
    }

    /**
     * HTTP 호출 + 오류 판정. 두 종류의 실패가 여기로 모인다:
     *  1. **전송 계층 실패** — 연결 거부 · 타임아웃 · 5xx. [RestClientException]으로 온다.
     *  2. **본문 오류** — KTO는 오류도 **HTTP 200**으로 주며 `resultCode`에만 나타난다.
     *
     * 둘 다 도메인 오류로 바꾼다. 그대로 두면 업스트림 장애가 INTERNAL로 새어 나가
     * `UPSTREAM_UNAVAILABLE`이 도달 불가능해진다(§5의 기존 결함).
     */
    private fun <I : Any> call(uri: URI, type: ParameterizedTypeReference<TourStatsResponse<I>>):
        TourStatsResponse<I> {
        val response = try {
            ktoBigDataRestClient.get().uri(uri).retrieve().body(type)
                ?: throw UpstreamUnavailableException("KTO 빅데이터 응답이 비어 있습니다.")
        } catch (e: RestClientException) {
            throw translate(e)
        }

        if (response.isRateLimited) {
            // 한도 초과를 장애와 구분해 표면화한다 — 조용히 실패하면 데이터가 비는 것을
            // 나중에야 알게 된다(설계 §2.3).
            throw UpstreamRateLimitedException("KTO 빅데이터 호출 한도를 초과했습니다.")
        }
        if (!response.isSuccess) {
            log.warn("KTO 빅데이터 오류 응답: code={} msg={}", response.resultCode, response.resultMessage)
            throw UpstreamUnavailableException("KTO 빅데이터 오류 응답: ${response.resultCode}")
        }
        return response
    }

    /**
     * 전송 계층 예외 → 도메인 오류(R8).
     *
     * HTTP 429와 공공데이터포털의 한도 초과 응답만 [UpstreamRateLimitedException]이고 나머지는
     * 전부 [UpstreamUnavailableException]이다 — 연결 실패 · 타임아웃 · 4xx · 5xx · 역직렬화 실패.
     * 4xx도 여기 포함되는 것은 배치 관점에서 재시도 불가라는 결론이 같기 때문이다.
     */
    private fun translate(e: RestClientException): RuntimeException {
        val status = (e as? RestClientResponseException)?.statusCode?.value()
        if (status == HTTP_TOO_MANY_REQUESTS) {
            return UpstreamRateLimitedException("KTO 빅데이터 호출 한도를 초과했습니다.", e)
        }
        // **URI는 절대 싣지 않는다** — serviceKey가 로그로 샌다. 대신 원인 예외의 클래스명까지
        // 남긴다: 역직렬화 실패와 네트워크 실패가 여기서 같은 메시지로 합쳐지면 원인을 가릴 수
        // 없다(실측: `"items": ""` 역직렬화 실패가 네트워크 장애처럼 보였다).
        log.warn(
            "관광통계 호출 실패: {} status={} cause={}",
            e.javaClass.simpleName,
            status,
            e.cause?.javaClass?.simpleName ?: "-",
        )
        return UpstreamUnavailableException("관광통계 호출에 실패했습니다(${e.javaClass.simpleName}).", e)
    }

    /**
     * 요청 URI. **serviceKey는 이미 인코딩된 값이라 `build(true)`로 재인코딩을 막는다** —
     * 재인코딩하면 `%2B`가 `%252B`가 되어 인증이 실패한다(TourApiClient와 같은 처리).
     */
    private fun uriFor(
        service: String,
        operation: String,
        pageNo: Int,
        numOfRows: Int,
        extra: UriComponentsBuilder.() -> Unit,
    ): URI =
        UriComponentsBuilder.fromUriString(baseUrl(service))
            .path("/$operation")
            .queryParam("serviceKey", properties.serviceKey)
            .queryParam("MobileOS", MOBILE_OS)
            .queryParam("MobileApp", MOBILE_APP)
            .queryParam("_type", "json")
            .queryParam("pageNo", pageNo)
            .queryParam("numOfRows", numOfRows)
            .apply(extra)
            .build(true)
            .toUri()

    private fun baseUrl(service: String): String =
        properties.baseUrls[service]
            ?: throw UpstreamUnavailableException("KTO 빅데이터 base-url이 설정되지 않았습니다: $service")

    private companion object {
        const val OP_VISITOR_COUNT = "locgoRegnVisitrDDList"
        const val OP_CONCENTRATION_RATE = "tatsCnctrRatedList"
        const val OP_STAY_INTENSITY = "areaTarSjrnDsList"
        const val OP_SPENDING_INTENSITY = "areaTarExpDsList"

        const val MOBILE_OS = "ETC"
        const val MOBILE_APP = "ieuri"
        const val HTTP_TOO_MANY_REQUESTS = 429

        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMMdd")
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMM")

        /** 제네릭 응답 봉투는 타입 소거 때문에 [ParameterizedTypeReference]로 실체화해야 한다. */
        val VISITOR_COUNT_TYPE =
            object : ParameterizedTypeReference<TourStatsResponse<TourStatsVisitorCountItem>>() {}
        val CONCENTRATION_RATE_TYPE =
            object : ParameterizedTypeReference<TourStatsResponse<TourStatsConcentrationRateItem>>() {}
        val DEMAND_INTENSITY_TYPE =
            object : ParameterizedTypeReference<TourStatsResponse<TourStatsDemandIntensityItem>>() {}
    }
}
