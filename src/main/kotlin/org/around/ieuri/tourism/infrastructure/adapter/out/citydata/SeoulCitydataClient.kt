package org.around.ieuri.tourism.infrastructure.adapter.out.citydata

import org.around.ieuri.tourism.application.port.out.client.SeoulCitydataPort
import org.around.ieuri.tourism.application.port.out.client.SeoulCitydataSnapshot
import org.around.ieuri.tourism.infrastructure.adapter.out.citydata.mapper.toSnapshotOrNull
import org.around.ieuri.tourism.infrastructure.config.SeoulCitydataProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.requiredBody
import org.springframework.web.util.UriComponentsBuilder

/**
 * 서울시 실시간 도시데이터 어댑터(SPEC-0014 §3). [SeoulCitydataPort]의 유일한 구현체로,
 * 업스트림 세부(필드명·구역명 규약·오류 코드)를 여기 가둔다.
 *
 * **호출 수를 세지 않는다.** 일일 상한은 업스트림이 강제하고 초과하면 `RESULT.CODE`로
 * 알려주므로, 우리가 임의의 숫자를 들고 따로 세는 것은 틀리기만 쉽다 — 서버가 여러 대면
 * 프로세스마다 제각각 세고, 상한값 자체도 추측이다. 호출량은 캐시(최소 재호출 간격)와
 * 뷰포트 범위로 줄인다.
 *
 * **실패를 예외로 올리지 않는다** — 전부 null로 돌려주고 호출자가 degraded로 표현한다(R11).
 * 지도의 한 레이어가 없는 것을 지도 전체의 실패로 만들지 않기 위해서다.
 *
 * 호출은 `citydata` 하나로 통일한다. 인구만 필요한 경로에서는 `citydata_ppltn`(경량)이
 * 더 싸지만, 같은 구역에 대해 블록별로 나눠 부르면 호출이 배가된다 — 일일 상한이 있는
 * 업스트림에서는 응답 크기보다 호출 수가 중요하다.
 */
@Component
class SeoulCitydataClient(
    private val properties: SeoulCitydataProperties,
    private val seoulCitydataRestClient: RestClient,
) : SeoulCitydataPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun fetch(areaCode: String, areaName: String): SeoulCitydataSnapshot? {
        // 키가 비어 있으면 기동은 되고 이 레이어만 degraded다(R11) — 로컬에서 키 없이
        // 지도를 띄울 수 있어야 한다.
        if (properties.apiKey.isBlank()) return null

        val response = try {
            seoulCitydataRestClient.get().uri(uriFor(areaName)).retrieve()
                .requiredBody<SeoulCitydataResponse>()
        } catch (e: Exception) {
            // 장애·타임아웃·역직렬화 실패 모두 degraded로 흡수한다.
            log.warn("서울 실시간 도시데이터 호출 실패({}): {}", areaCode, e.javaClass.simpleName)
            return null
        }

        // **업스트림은 실패도 HTTP 200으로 준다** — RESULT.CODE를 봐야 안다.
        // 일일 쿼터 초과도 여기로 온다. 우리가 호출 수를 세지 않는 이유가 이것이다:
        // 상한은 업스트림이 강제하고 그 사실을 응답으로 알려주므로, 임의의 숫자를 코드에
        // 박아 두고 따로 세는 것은 틀리기만 쉽다(서버가 여러 대면 더 그렇다).
        val result = response.result
        if (result != null && !result.isSuccess) {
            log.warn("서울 실시간 도시데이터 오류 응답({}): {} {}", areaCode, result.code, result.message)
            return null
        }

        // 불일치·파싱 실패는 매퍼가 null로 알린다(R15). 원인 추적을 위해 여기서만 로깅한다.
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
     * `{key}/json/citydata/{startIndex}/{endIndex}/{장소}` — 열린데이터광장 공통 REST 규약이다.
     *
     * **인덱스가 `1/1`인 이유**: 두 값은 결과 행의 시작·끝 위치이며 플랫폼 전체가 공유하는
     * 페이징 규약이다(한 번에 최대 1,000행). `citydata`는 **한 번에 1개 장소만** 주므로
     * 행이 언제나 1개뿐이라 `1/1`이 맞다. 더 큰 범위를 요청해도 같은 1건이 오지만, 있지도
     * 않은 행을 요구하는 셈이라 의미가 없다.
     *
     * 장소는 **국문명**으로 지정한다(장소코드도 되지만 우리는 이름을 쓴다). 경로 세그먼트라
     * 인코딩이 필요하다 — 구역명에 `·`과 공백이 들어간다.
     */
    private fun uriFor(areaName: String) =
        UriComponentsBuilder.fromUriString(properties.baseUrl)
            .pathSegment(properties.apiKey, "json", SERVICE, "1", "1", areaName)
            .build()
            .encode()
            .toUri()

    private companion object {
        /** 인구·상권·문화행사 세 블록을 한 번에 주는 오퍼레이션. */
        const val SERVICE = "citydata"
    }
}
