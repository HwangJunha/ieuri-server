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
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/**
 * 서울시 실시간 도시데이터 어댑터(SPEC-0014 §3). [SeoulCitydataPort]의 유일한 구현체로,
 * 업스트림 세부(필드명·구역명 규약·쿼터)를 여기 가둔다.
 *
 * **실패를 예외로 올리지 않는다** — 전부 null로 돌려주고 호출자가 degraded로 표현한다(R11).
 * 지도의 한 레이어가 없는 것을 지도 전체의 실패로 만들지 않기 위해서다.
 *
 * 호출은 `citydata` 하나로 통일한다. 인구만 필요한 경로에서는 `citydata_ppltn`(경량)이
 * 더 싸지만, 같은 구역에 대해 블록별로 나눠 부르면 호출이 배가된다 — 일일 1000건
 * 제약에서는 응답 크기보다 호출 수가 중요하다.
 */
@Component
class SeoulCitydataClient(
    private val properties: SeoulCitydataProperties,
    private val seoulCitydataRestClient: RestClient,
    private val clock: Clock,
) : SeoulCitydataPort {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 일일 호출 카운터. 자정이 지나면 리셋한다. */
    private val callCount = AtomicInteger(0)

    @Volatile
    private var counterDate: LocalDate = LocalDate.now(clock)

    override fun fetch(areaCode: String, areaName: String): SeoulCitydataSnapshot? {
        // 키가 비어 있으면 기동은 되고 이 레이어만 degraded다(R11) — 로컬에서 키 없이
        // 지도를 띄울 수 있어야 한다.
        if (properties.apiKey.isBlank()) return null
        if (!tryAcquireCall()) {
            log.warn("서울 실시간 도시데이터 일일 호출 상한 도달, 캐시만 사용한다: {}", properties.dailyCallLimit)
            return null
        }

        val response = try {
            seoulCitydataRestClient.get().uri(uriFor(areaName)).retrieve()
                .requiredBody<SeoulCitydataResponse>()
        } catch (e: Exception) {
            // 장애·타임아웃·역직렬화 실패 모두 degraded로 흡수한다.
            log.warn("서울 실시간 도시데이터 호출 실패({}): {}", areaCode, e.javaClass.simpleName)
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
     * 업스트림은 구역 **국문명**으로 조회한다(코드가 아니다). 경로 세그먼트라 인코딩이
     * 필요하다 — 구역명에 `·`과 공백이 들어간다.
     */
    private fun uriFor(areaName: String) =
        UriComponentsBuilder.fromUriString(properties.baseUrl)
            .pathSegment(properties.apiKey, "json", SERVICE, "1", "5", areaName)
            .build()
            .encode()
            .toUri()

    /**
     * 일일 상한 안에서 호출 1건을 확보한다(spec §9). 상한에 닿으면 업스트림을 더 부르지
     * 않고 캐시만 쓴다 — 쿼터를 넘겨 차단당하는 것보다 낡은 데이터를 주는 편이 낫다.
     *
     * 자정 기준으로 리셋한다. 동시 호출이 카운터를 살짝 넘길 수 있으나(리셋과 증가가 한
     * 원자 단위가 아니다), 쿼터 1000건에서 몇 건의 오차는 문제가 되지 않는다 — 정확한
     * 회계가 목적이 아니라 폭주를 막는 것이 목적이다.
     */
    private fun tryAcquireCall(): Boolean {
        val today = LocalDate.now(clock)
        if (today != counterDate) {
            counterDate = today
            callCount.set(0)
        }
        return callCount.incrementAndGet() <= properties.dailyCallLimit
    }

    private companion object {
        /** 인구·상권·문화행사 세 블록을 한 번에 주는 오퍼레이션. */
        const val SERVICE = "citydata"
    }
}
