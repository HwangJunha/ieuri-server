package org.around.ieuri.tourism.infrastructure.adapter.out.client.climateindex

import org.around.ieuri.tourism.application.command.SyncClimateIndexCommand
import org.around.ieuri.tourism.application.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.port.out.client.ClimateIndexSyncPort
import org.around.ieuri.tourism.application.port.out.client.TourStatsSyncPage
import org.around.ieuri.tourism.infrastructure.adapter.out.client.climateindex.dto.ClimateIndexResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.client.climateindex.mapper.toSyncCommandOrNull
import org.around.ieuri.tourism.infrastructure.config.ClimateIndexProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 기상청 관광기후지수 어댑터(SPEC-0015 · V49). [ClimateIndexSyncPort]의 유일한 구현체로,
 * 업스트림 세부(파라미터 대소문자 · 성공 코드 · 오류 봉투)를 여기 가둔다.
 *
 * **`CITY_AREA_ID`를 보내지 않는다** — 선택 파라미터라 생략하면 전국이 한 응답에 온다
 * (실측 2026-08-11: 235개 시군구 472행). KTO 집중률처럼 지역을 순회할 이유가 없다.
 *
 * **예외를 변환한다**(R8 · 설계 §5) — 전송 실패는 `UPSTREAM_UNAVAILABLE`, 한도 초과는
 * `UPSTREAM_RATE_LIMITED`. **빈 응답은 장애가 아니다.**
 *
 * serviceKey는 로그에 남기지 않는다. URI를 통째로 로깅하지 않는 것도 같은 이유다.
 */
@Component
class ClimateIndexClient(
    private val properties: ClimateIndexProperties,
    private val tourApiRestClient: RestClient,
) : ClimateIndexSyncPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun fetchClimateIndices(
        baseDate: LocalDate,
        days: Int,
        pageNo: Int,
        numOfRows: Int,
    ): TourStatsSyncPage<SyncClimateIndexCommand> {
        val response = call(uriFor(baseDate, days, pageNo, numOfRows))
        val items = response.items
        val records = items.mapNotNull { it.toSyncCommandOrNull() }
        return TourStatsSyncPage(
            records = records,
            totalCount = response.totalCount,
            droppedCount = items.size - records.size,
        )
    }

    /**
     * HTTP 호출 + 오류 판정. 두 종류의 실패가 여기로 모인다:
     *  1. **전송 계층 실패** — 연결 거부 · 타임아웃 · 5xx. [RestClientException]으로 온다.
     *  2. **본문 오류** — 오류도 **HTTP 200**으로 오며 `resultCode`에만 나타난다. 인증 실패
     *     등에서는 아예 다른 봉투(`OpenAPI_ServiceResponse`)라 header 자체가 없다.
     */
    private fun call(uri: URI): ClimateIndexResponse {
        val response = try {
            tourApiRestClient.get().uri(uri).retrieve().body(ClimateIndexResponse::class.java)
                ?: throw UpstreamUnavailableException("관광기후지수 응답이 비어 있습니다.")
        } catch (e: RestClientException) {
            throw translate(e)
        }

        if (!response.hasHeader) {
            // 정상 봉투가 아니다 — 인증 실패·서비스 중지 등에서 오는 다른 형태다. 성공으로
            // 통과시키면 0건이 "데이터 없음"으로 조용히 흡수된다.
            throw UpstreamUnavailableException("관광기후지수 응답 형식이 올바르지 않습니다.")
        }
        if (response.isRateLimited) {
            throw UpstreamRateLimitedException("관광기후지수 호출 한도를 초과했습니다.")
        }
        if (!response.isSuccess) {
            log.warn("관광기후지수 오류 응답: code={} msg={}", response.resultCode, response.resultMessage)
            throw UpstreamUnavailableException("관광기후지수 오류 응답: ${response.resultCode}")
        }
        return response
    }

    /**
     * 전송 계층 예외 → 도메인 오류(R8).
     *
     * HTTP 429만 [UpstreamRateLimitedException]이고 나머지는 전부 [UpstreamUnavailableException]
     * 이다 — 연결 실패 · 타임아웃 · 4xx · 5xx · 역직렬화 실패. 4xx도 여기 포함되는 것은 배치
     * 관점에서 재시도 불가라는 결론이 같기 때문이다.
     */
    private fun translate(e: RestClientException): RuntimeException {
        val status = (e as? RestClientResponseException)?.statusCode?.value()
        if (status == HTTP_TOO_MANY_REQUESTS) {
            return UpstreamRateLimitedException("관광기후지수 호출 한도를 초과했습니다.", e)
        }
        // **URI는 절대 싣지 않는다** — serviceKey가 로그로 샌다. 원인 예외의 클래스명까지 남겨
        // 역직렬화 실패와 네트워크 실패가 한 메시지로 합쳐지지 않게 한다.
        log.warn(
            "관광기후지수 호출 실패: {} status={} cause={}",
            e.javaClass.simpleName,
            status,
            e.cause?.javaClass?.simpleName ?: "-",
        )
        return UpstreamUnavailableException("관광기후지수 호출에 실패했습니다(${e.javaClass.simpleName}).", e)
    }

    /**
     * 요청 URI. **serviceKey는 이미 인코딩된 값이라 `build(true)`로 재인코딩을 막는다** —
     * 재인코딩하면 `%2B`가 `%252B`가 되어 인증이 실패한다.
     *
     * `CURRENT_DATE`·`DAY`는 **대문자 파라미터명이다**(실측) — 소문자로 보내면 필수 파라미터
     * 누락이 된다. `CITY_AREA_ID`는 의도적으로 생략한다(전국 일괄).
     */
    private fun uriFor(baseDate: LocalDate, days: Int, pageNo: Int, numOfRows: Int): URI =
        UriComponentsBuilder.fromUriString(properties.baseUrl)
            .path("/$OPERATION")
            .queryParam("serviceKey", properties.serviceKey)
            .queryParam("dataType", "JSON")
            .queryParam("pageNo", pageNo)
            .queryParam("numOfRows", numOfRows)
            .queryParam(PARAM_CURRENT_DATE, baseDate.format(DATE_FORMAT))
            .queryParam(PARAM_DAY, days)
            .build(true)
            .toUri()

    private companion object {
        const val OPERATION = "getCityTourClmIdx1"
        const val PARAM_CURRENT_DATE = "CURRENT_DATE"
        const val PARAM_DAY = "DAY"
        const val HTTP_TOO_MANY_REQUESTS = 429

        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMMdd")
    }
}
