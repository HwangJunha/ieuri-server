package org.around.ieuri.tourism.adapter.client.kto

import org.around.ieuri.tourism.adapter.client.kto.dto.KtoBigDataResponse
import org.around.ieuri.tourism.adapter.client.kto.dto.KtoConcentrationRateItem
import org.around.ieuri.tourism.adapter.client.kto.dto.KtoDemandIntensityItem
import org.around.ieuri.tourism.adapter.client.kto.dto.KtoVisitorCountItem
import org.around.ieuri.tourism.adapter.client.kto.mapper.toSyncCommandOrNull
import org.around.ieuri.tourism.adapter.config.KtoBigDataProperties
import org.around.ieuri.tourism.application.region.provided.dto.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.region.provided.dto.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.region.provided.dto.SyncVisitorCountCommand
import org.around.ieuri.tourism.application.region.required.RegionMetricSyncPage
import org.around.ieuri.tourism.application.region.required.RegionMetricSyncPort
import org.around.ieuri.tourism.application.shared.provided.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.shared.provided.exception.UpstreamUnavailableException
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
 * KTO 빅데이터 4종 어댑터(SPEC-0015 §5). 업스트림 세부(서비스 경로·필드명·오류 코드)를 여기 가둔다.
 * - 전송·본문 오류를 도메인 예외로 변환한다([translate], R8).
 * - 집중률은 시군구마다 호출한다(1회 실행 252 호출). page-size를 낮추면 일 한도 1,000건에 걸린다.
 * - serviceKey가 담긴 URI는 로그에 남기지 않는다.
 */
@Component
class KtoBigDataClient(
    private val properties: KtoBigDataProperties,
    private val ktoBigDataRestClient: RestClient,
) : RegionMetricSyncPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun fetchVisitorCounts(
        startDate: LocalDate,
        endDate: LocalDate,
        pageNo: Int,
        numOfRows: Int,
    ): RegionMetricSyncPage<SyncVisitorCountCommand> {
        val uri = uriFor(KtoBigDataProperties.SERVICE_DATA_LAB, OP_VISITOR_COUNT, pageNo, numOfRows) {
            // 지역 필터가 없어 날짜 구간만 주면 전 지역이 온다(§4.3).
            queryParam("startYmd", startDate.format(DATE_FORMAT))
            queryParam("endYmd", endDate.format(DATE_FORMAT))
        }
        return fetchPage(uri, VISITOR_COUNT_TYPE) { it.toSyncCommandOrNull() }
    }

    /**
     * 집중률. 날짜 파라미터를 보내면 `INVALID_REQUEST_PARAMETER_ERROR`가 온다.
     * - 시군구 하나에 향후 예측일이 전부 오며, 대상일은 행의 `baseYmd`에 실린다.
     */
    override fun fetchConcentrationRates(
        areaCd: String,
        signguCd: String,
        pageNo: Int,
        numOfRows: Int,
    ): RegionMetricSyncPage<SyncConcentrationRateCommand> {
        val uri = uriFor(KtoBigDataProperties.SERVICE_TATS_CNCTR_RATE, OP_CONCENTRATION_RATE, pageNo, numOfRows) {
            queryParam("areaCd", areaCd)
            queryParam("signguCd", signguCd)
        }
        return fetchPage(uri, CONCENTRATION_RATE_TYPE) { it.toSyncCommandOrNull() }
    }

    override fun fetchStayIntensities(
        areaCd: String,
        baseMonth: YearMonth,
        pageNo: Int,
        numOfRows: Int,
    ): RegionMetricSyncPage<SyncDemandIntensityCommand> = fetchDemandIntensity(
        OP_STAY_INTENSITY,
        PARAM_STAY_INDEX_CODE,
        properties.stayIndexCode,
        areaCd,
        baseMonth,
        pageNo,
        numOfRows,
    )

    override fun fetchSpendingIntensities(
        areaCd: String,
        baseMonth: YearMonth,
        pageNo: Int,
        numOfRows: Int,
    ): RegionMetricSyncPage<SyncDemandIntensityCommand> = fetchDemandIntensity(
        OP_SPENDING_INTENSITY,
        PARAM_SPENDING_INDEX_CODE,
        properties.spendingIndexCode,
        areaCd,
        baseMonth,
        pageNo,
        numOfRows,
    )

    /**
     * 체류·소비 강도. `areaCd`만 주면 그 시도의 시군구 전체가 오며, 시도 집계 행은 R9 필터가 거른다.
     * - 월 인자는 `baseYm` 하나다.
     * - 지표 코드가 빠지면 오류 없이 `totalCount:0`이 온다. 파라미터명은 오퍼레이션마다 다르다.
     */
    private fun fetchDemandIntensity(
        operation: String,
        indexParamName: String,
        indexCode: String,
        areaCd: String,
        baseMonth: YearMonth,
        pageNo: Int,
        numOfRows: Int,
    ): RegionMetricSyncPage<SyncDemandIntensityCommand> {
        val uri = uriFor(KtoBigDataProperties.SERVICE_AREA_TAR_DEM_DS, operation, pageNo, numOfRows) {
            queryParam("areaCd", areaCd)
            queryParam("baseYm", baseMonth.format(MONTH_FORMAT))
            queryParam(indexParamName, indexCode)
        }
        return fetchPage(uri, DEMAND_INTENSITY_TYPE) { it.toSyncCommandOrNull() }
    }

    /**
     * 한 페이지를 적재 명령으로 옮긴다. 매핑 실패 행은 드랍하고 건수만 센다.
     * - 빈 응답은 예외가 아니라 빈 [RegionMetricSyncPage]다(R8).
     */
    private fun <I : Any, R : Any> fetchPage(
        uri: URI,
        type: ParameterizedTypeReference<KtoBigDataResponse<I>>,
        map: (I) -> R?,
    ): RegionMetricSyncPage<R> {
        val response = call(uri, type)
        val items = response.items
        val records = items.mapNotNull(map)
        return RegionMetricSyncPage(
            records = records,
            totalCount = response.totalCount,
            droppedCount = items.size - records.size,
        )
    }

    /** HTTP 호출 + 오류 판정. 전송 실패와 HTTP 200에 `resultCode`로만 오는 본문 오류를 모두 도메인 오류로 바꾼다. */
    private fun <I : Any> call(uri: URI, type: ParameterizedTypeReference<KtoBigDataResponse<I>>): KtoBigDataResponse<I> {
        val response = try {
            ktoBigDataRestClient.get().uri(uri).retrieve().body(type)
                ?: throw UpstreamUnavailableException("KTO 빅데이터 응답이 비어 있습니다.")
        } catch (e: RestClientException) {
            throw translate(e)
        }

        if (response.isRateLimited) {
            // 한도 초과는 장애와 구분해 표면화한다.
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
     * - HTTP 429·한도 초과만 [UpstreamRateLimitedException], 나머지(4xx 포함)는 [UpstreamUnavailableException]이다.
     */
    private fun translate(e: RestClientException): RuntimeException {
        val status = (e as? RestClientResponseException)?.statusCode?.value()
        if (status == HTTP_TOO_MANY_REQUESTS) {
            return UpstreamRateLimitedException("KTO 빅데이터 호출 한도를 초과했습니다.", e)
        }
        // URI는 싣지 않는다(serviceKey 노출). 원인 예외의 클래스명은 남긴다.
        log.warn(
            "관광통계 호출 실패: {} status={} cause={}",
            e.javaClass.simpleName,
            status,
            e.cause?.javaClass?.simpleName ?: "-",
        )
        return UpstreamUnavailableException("관광통계 호출에 실패했습니다(${e.javaClass.simpleName}).", e)
    }

    /** 요청 URI. serviceKey는 이미 인코딩된 값이라 `build(true)`로 재인코딩을 막는다(`%2B`→`%252B`면 인증 실패). */
    private fun uriFor(
        service: String,
        operation: String,
        pageNo: Int,
        numOfRows: Int,
        extra: UriComponentsBuilder.() -> Unit,
    ): URI = UriComponentsBuilder.fromUriString(baseUrl(service))
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

    private fun baseUrl(service: String): String = properties.baseUrls[service]
        ?: throw UpstreamUnavailableException("KTO 빅데이터 base-url이 설정되지 않았습니다: $service")

    private companion object {
        const val OP_VISITOR_COUNT = "locgoRegnVisitrDDList"
        const val OP_CONCENTRATION_RATE = "tatsCnctrRatedList"
        const val OP_STAY_INTENSITY = "areaTarSjrnDsList"
        const val OP_SPENDING_INTENSITY = "areaTarExpDsList"

        /** 지표 코드 파라미터명. 오퍼레이션마다 다르다. */
        const val PARAM_STAY_INDEX_CODE = "tarSjrnDsIxCd"
        const val PARAM_SPENDING_INDEX_CODE = "tarExpDsIxCd"

        const val MOBILE_OS = "ETC"
        const val MOBILE_APP = "ieuri"
        const val HTTP_TOO_MANY_REQUESTS = 429

        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMMdd")
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMM")

        /** 제네릭 응답 봉투는 타입 소거 때문에 [ParameterizedTypeReference]로 실체화해야 한다. */
        val VISITOR_COUNT_TYPE =
            object : ParameterizedTypeReference<KtoBigDataResponse<KtoVisitorCountItem>>() {}
        val CONCENTRATION_RATE_TYPE =
            object : ParameterizedTypeReference<KtoBigDataResponse<KtoConcentrationRateItem>>() {}
        val DEMAND_INTENSITY_TYPE =
            object : ParameterizedTypeReference<KtoBigDataResponse<KtoDemandIntensityItem>>() {}
    }
}
