package org.around.ieuri.tourism.infrastructure

import io.mockk.mockk
import org.around.ieuri.tourism.domain.vo.CongestionLevel
import org.around.ieuri.tourism.infrastructure.adapter.out.client.seoulopendata.SeoulOpenDataClient
import org.around.ieuri.tourism.infrastructure.adapter.out.client.seoulopendata.SeoulOpenDataResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.client.seoulopendata.mapper.toSnapshotOrNull
import org.around.ieuri.tourism.infrastructure.config.SeoulOpenDataProperties
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 응답 → 스냅샷 변환의 구역 코드 검증(SPEC-0014 R15).
 *
 * **존재하지 않는 구역을 요청해도 오류 없이 다른 구역 데이터가 돌아오는 동작이 관측됐다**
 * (2026-08-10 샘플 키에서 어떤 구역을 물어도 POI009가 왔다). 이 검증이 없으면 잘못된 구역
 * 데이터가 올바른 키로 캐시되고, 그 순간 원인 추적이 어려워진다.
 *
 * HTTP 호출은 여기서 다루지 않는다 — 변환 함수만 본다.
 */
class SeoulOpenDataClientTest {

    private fun responseOf(areaCode: String?) = SeoulOpenDataResponse(
        cityData = SeoulOpenDataResponse.CityData(
            areaCode = areaCode,
            population = listOf(
                SeoulOpenDataResponse.Population(
                    congestionLevel = "여유",
                    populationMin = "4000",
                    populationMax = "4500",
                    observedAt = "2026-08-10 05:45",
                ),
            ),
        ),
    )

    @Test
    fun `요청한 구역과 응답 구역이 같으면 변환된다`() {
        val snapshot = responseOf("POI009").toSnapshotOrNull("POI009")

        assertNotNull(snapshot)
        assertEquals("POI009", snapshot.areaCode)
        assertEquals(CongestionLevel.RELAXED, snapshot.congestion?.level)
    }

    @Test
    fun `응답 구역이 다르면 버린다`() {
        // AC R15. 이 null이 "캐시에 쓰지 않는다"로 이어진다.
        assertNull(responseOf("POI014").toSnapshotOrNull("POI009"))
    }

    @Test
    fun `응답에 구역 코드가 없으면 버린다`() {
        assertNull(responseOf(null).toSnapshotOrNull("POI009"))
    }

    @Test
    fun `대소문자는 구분하지 않는다`() {
        // 업스트림 표기가 흔들려도 같은 구역이면 통과시킨다.
        assertNotNull(responseOf("poi009").toSnapshotOrNull("POI009"))
    }

    @Test
    fun `CITYDATA가 없으면 버린다`() {
        assertNull(SeoulOpenDataResponse(cityData = null).toSnapshotOrNull("POI009"))
    }

    @Test
    fun `인구 블록이 비어도 스냅샷 자체는 만들어진다`() {
        // 문화행사만 있는 응답도 있을 수 있다 — 혼잡도만 null이 되고 나머지는 살린다.
        val response = SeoulOpenDataResponse(
            cityData = SeoulOpenDataResponse.CityData(areaCode = "POI009", population = emptyList()),
        )

        val snapshot = response.toSnapshotOrNull("POI009")

        assertNotNull(snapshot)
        assertNull(snapshot.congestion)
    }

    @Test
    fun `키가 비어 있으면 호출하지 않고 null이다`() {
        // 로컬에서 키 없이 지도를 띄울 수 있어야 한다(R11) — RestClient가 mockk라
        // 실제로 호출하면 예외가 난다. 즉 null 반환 자체가 "호출하지 않았다"의 증거다.
        val keyless = SeoulOpenDataClient(
            properties = SeoulOpenDataProperties(apiKey = ""),
            seoulOpenDataRestClient = mockk<RestClient>(),
        )

        assertNull(keyless.fetch("POI009", "광화문·덕수궁"))
    }

    @Test
    fun `RESULT 코드가 정상이면 성공으로 본다`() {
        // 업스트림은 실패도 HTTP 200으로 주므로 RESULT.CODE가 유일한 판정 근거다.
        assertEquals(true, SeoulOpenDataResponse.Result(code = "INFO-000").isSuccess)
    }

    @Test
    fun `RESULT 코드가 정상이 아니면 실패로 본다`() {
        // INFO-100(인증키 무효)·쿼터 초과 등이 전부 여기로 온다. 우리가 호출 수를 세는
        // 대신 업스트림이 알려주는 이 값을 믿는다.
        assertEquals(false, SeoulOpenDataResponse.Result(code = "INFO-100").isSuccess)
        assertEquals(false, SeoulOpenDataResponse.Result(code = null).isSuccess)
    }
}
