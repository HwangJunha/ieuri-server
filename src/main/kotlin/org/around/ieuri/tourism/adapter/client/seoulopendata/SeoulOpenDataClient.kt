package org.around.ieuri.tourism.adapter.client.seoulopendata

import org.around.ieuri.tourism.adapter.client.seoulopendata.mapper.toSnapshotOrNull
import org.around.ieuri.tourism.adapter.config.SeoulOpenDataProperties
import org.around.ieuri.tourism.application.congestion.required.SeoulOpenDataPort
import org.around.ieuri.tourism.application.congestion.required.dto.SeoulOpenDataSnapshot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.requiredBody
import org.springframework.web.util.UriComponentsBuilder

/**
 * 서울시 실시간 도시데이터 어댑터(SPEC-0014 §3). 업스트림 세부(필드명·구역명 규약·오류 코드)를 여기 가둔다.
 * - 호출 수를 세지 않는다 — 일일 상한은 업스트림이 `RESULT.CODE`로 알린다.
 * - 실패를 예외로 올리지 않고 null로 돌려준다. 호출자가 degraded로 표현한다(R11).
 */
@Component
class SeoulOpenDataClient(
    private val properties: SeoulOpenDataProperties,
    private val seoulOpenDataRestClient: RestClient,
) : SeoulOpenDataPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun fetch(areaCode: String, areaName: String): SeoulOpenDataSnapshot? {
        // 키가 비어 있으면 기동은 되고 이 레이어만 degraded다(R11).
        if (properties.apiKey.isBlank()) return null

        val response = try {
            seoulOpenDataRestClient.get().uri(uriFor(areaName)).retrieve()
                .requiredBody<SeoulOpenDataResponse>()
        } catch (e: Exception) {
            // 장애·타임아웃·역직렬화 실패 모두 degraded로 흡수한다.
            log.warn("서울 실시간 도시데이터 호출 실패({}): {}", areaCode, e.javaClass.simpleName)
            return null
        }

        // 업스트림은 실패(일일 쿼터 초과 포함)도 HTTP 200으로 준다 — RESULT.CODE를 봐야 안다.
        val result = response.result
        if (result != null && !result.isSuccess) {
            log.warn("서울 실시간 도시데이터 오류 응답({}): {} {}", areaCode, result.code, result.message)
            return null
        }

        // 불일치·파싱 실패는 매퍼가 null로 알린다(R15). 로깅은 여기서만 한다.
        return response.toSnapshotOrNull(areaCode)
            ?: null.also {
                log.warn(
                    "서울 실시간 도시데이터 응답을 쓸 수 없다(구역 코드 불일치 가능): 요청={} 응답={}",
                    areaCode,
                    response.cityData?.areaCode,
                )
            }
    }

    /**
     * `{key}/json/seoulOpenData/{startIndex}/{endIndex}/{장소}` — 열린데이터광장 공통 REST 규약이다.
     * - 한 번에 1개 장소만 오므로 인덱스는 `1/1`이다.
     * - 장소는 국문명이며 `·`·공백이 들어가 인코딩이 필요하다.
     */
    private fun uriFor(areaName: String) = UriComponentsBuilder.fromUriString(properties.baseUrl)
        .pathSegment(properties.apiKey, "json", SERVICE, "1", "1", areaName)
        .build()
        .encode()
        .toUri()

    private companion object {
        /** 인구·상권·문화행사 세 블록을 한 번에 주는 오퍼레이션. */
        const val SERVICE = "citydata"
    }
}
