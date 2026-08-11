package org.around.ieuri.tourism.infrastructure

import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiPetTourResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiPetTourSyncClient
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * detailPetTour2 수집 매핑(SPEC-0015 · V48). HTTP·역직렬화는 라이브러리 책임이라 배제하고
 * mapPage(DTO→적재 레코드)만 검증한다(TourApiSyncClientTest와 같은 관례).
 */
class TourApiPetTourSyncClientTest {

    private val properties = TourApiProperties(
        serviceKey = "dummy",
        baseUrls = mapOf("KO" to "https://ko"),
    )
    private val client = TourApiPetTourSyncClient(properties, RestClient.create())

    private fun item(contentid: String? = "126508") = TourApiPetTourResponse.Item(
        contentid = contentid,
        acmpyTypeCd = "전구역 동반가능",
        acmpyPsblCpam = "전 견종 동반 가능",
        acmpyNeedMtr = "목줄 착용",
        etcAcmpyInfo = "실내 동반 불가",
    )

    private fun response(items: List<TourApiPetTourResponse.Item>, totalCount: Int) =
        TourApiPetTourResponse(
            response = TourApiPetTourResponse.Response(
                body = TourApiPetTourResponse.Body(
                    totalCount = totalCount,
                    items = TourApiPetTourResponse.Items(item = items),
                ),
            ),
        )

    @Test
    fun `유효한 항목을 적재 레코드로 옮기고 총계를 그대로 전달한다`() {
        val page = client.mapPage(response(listOf(item("1"), item("2")), totalCount = 9_701))

        assertEquals(listOf("1", "2"), page.records.map { it.contentId })
        assertEquals(9_701, page.totalCount)
        assertEquals(0, page.droppedCount)
    }

    @Test
    fun `contentid 없는 항목만 드랍하고 나머지는 살린다 - 한 건이 페이지를 실패시키지 않는다`() {
        val page = client.mapPage(response(listOf(item("1"), item(null), item("3")), totalCount = 3))

        assertEquals(listOf("1", "3"), page.records.map { it.contentId })
        assertEquals(1, page.droppedCount)
    }

    @Test
    fun `빈 응답은 빈 페이지다 - 예외가 아니다 (R8)`() {
        val page = client.mapPage(response(emptyList(), totalCount = 0))

        assertTrue(page.records.isEmpty())
        assertEquals(0, page.totalCount)
        assertEquals(0, page.droppedCount)
        assertFalse(page.hasMore(pageNo = 1, numOfRows = 1_000))
    }

    @Test
    fun `hasMore는 이미 읽은 건수가 총계에 도달하면 false다`() {
        val page = client.mapPage(response(listOf(item()), totalCount = 9_701))

        assertTrue(page.hasMore(pageNo = 9, numOfRows = 1_000))
        // 10 * 1000 = 10,000 >= 9,701 — 더 읽을 것이 없다.
        assertFalse(page.hasMore(pageNo = 10, numOfRows = 1_000))
    }
}
