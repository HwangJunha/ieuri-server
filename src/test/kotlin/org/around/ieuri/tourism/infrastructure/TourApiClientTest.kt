package org.around.ieuri.tourism.infrastructure

import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.port.out.client.NearbySpotQuery
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.tourapi.TourApiClient
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * TourApiClient 파싱 — TourAPI 함정 5종(AC-7)과 필터·정규화·매핑(AC-3/4/5/15).
 * HTTP를 배제하고 응답 본문 파싱만 검증한다(parseResponse internal).
 */
class TourApiClientTest {

    private val properties = TourApiProperties(
        serviceKey = "dummy",
        baseUrls = mapOf("KO" to "https://ko", "EN" to "https://en"),
        categoryCodes = mapOf(
            "KO" to mapOf(SpotCategory.SHOPPING to 38, SpotCategory.TOURIST_SPOT to 12),
            "FOREIGN" to mapOf(SpotCategory.SHOPPING to 79, SpotCategory.TOURIST_SPOT to 76),
        ),
        excludeLclssystm2 = listOf("EX999"),
    )
    private val client = TourApiClient(properties, JsonMapper.builder().build())

    private val query = NearbySpotQuery(
        locale = DomainLocale.KO,
        center = Coordinates(37.5665, 126.9780),
        radiusMeters = 20000,
        category = SpotCategory.TOURIST_SPOT,
        pageNo = 1,
        numOfRows = 100,
    )

    private fun ok(itemsJson: String, totalCount: Int = 1) = """
        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
        "body":{"numOfRows":100,"pageNo":1,"totalCount":$totalCount,"items":$itemsJson}}}
    """.trimIndent()

    private fun item(
        contentId: String = "100",
        image: String = "http://tong.visitkorea.or.kr/img/100.jpg",
        mapx: String = "126.98",
        mapy: String = "37.56",
        title: String = "경복궁",
        lcls2: String? = null,
        cpyrht: String? = null,
    ): String {
        val lcls = lcls2?.let { ""","lclsSystm2":"$it"""" } ?: ""
        val cpy = cpyrht?.let { ""","cpyrhtDivCd":"$it"""" } ?: ""
        return """{"contentid":"$contentId","firstimage":"$image","mapx":"$mapx","mapy":"$mapy","title":"$title","addr1":"서울","addr2":"종로구"$lcls$cpy}"""
    }

    @Test
    fun `함정3 - 0건이면 items가 빈 문자열이어도 빈 리스트`() {
        val result = client.parseResponse(ok("\"\"", totalCount = 0), query)
        assertTrue(result.spots.isEmpty())
    }

    @Test
    fun `함정4 - 1건이면 item이 객체여도 단일 원소 리스트`() {
        val result = client.parseResponse(ok("""{"item":${item()}}"""), query)
        assertEquals(1, result.spots.size)
        assertEquals("100", result.spots.single().id)
    }

    @Test
    fun `여러 건이면 item 배열을 그대로 매핑`() {
        val json = ok("""{"item":[${item("1")},${item("2")}]}""", totalCount = 2)
        val result = client.parseResponse(json, query)
        assertEquals(listOf("1", "2"), result.spots.map { it.id })
    }

    @Test
    fun `함정5 - 미정의 필드가 있어도 무시하고 파싱`() {
        val weird = """{"item":{"contentid":"7","firstimage":"http://x/7.jpg","mapx":"126.9","mapy":"37.5","title":"T","unknownField":"boom","nested":{"a":1}}}"""
        val result = client.parseResponse(ok(weird), query)
        assertEquals("7", result.spots.single().id)
    }

    @Test
    fun `함정2 - 오류가 XML이면 UpstreamUnavailable`() {
        val xml = """<?xml version="1.0"?><OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>22</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>"""
        assertFailsWith<UpstreamUnavailableException> { client.parseResponse(xml, query) }
    }

    @Test
    fun `resultCode가 0000이 아니면 UpstreamUnavailable`() {
        val err = """{"response":{"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},"body":{}}}"""
        assertFailsWith<UpstreamUnavailableException> { client.parseResponse(err, query) }
    }

    @Test
    fun `AC-3 - 이미지 URL이 https로 정규화된다`() {
        val result = client.parseResponse(ok("""{"item":${item(image = "http://tong.visitkorea.or.kr/a.jpg")}}"""), query)
        assertTrue(result.spots.single().thumbnailImageUrl.startsWith("https://"))
    }

    @Test
    fun `AC-3 - 이미지가 없는 항목은 응답에서 제외된다`() {
        val noImage = """{"item":{"contentid":"9","firstimage":"","mapx":"126.9","mapy":"37.5","title":"T"}}"""
        val result = client.parseResponse(ok(noImage, totalCount = 1), query)
        assertTrue(result.spots.isEmpty())
    }

    @Test
    fun `AC-4 - 비관광 lclsSystm2 코드 항목은 필터링된다`() {
        val json = ok("""{"item":[${item("1", lcls2 = "EX999")},${item("2", lcls2 = "OK001")}]}""", totalCount = 2)
        val result = client.parseResponse(json, query)
        assertEquals(listOf("2"), result.spots.map { it.id })
    }

    @Test
    fun `AC-15 - cpyrhtDivCd를 CopyrightType으로 매핑하고 빈 값도 최소 출처를 담는다`() {
        val json = ok("""{"item":[${item("1", cpyrht = "Type3")},${item("2", cpyrht = "Type1")},${item("3")}]}""", totalCount = 3)
        val result = client.parseResponse(json, query)
        val byId = result.spots.associateBy { it.id }
        assertEquals(CopyrightType.TYPE3, byId["1"]!!.copyright)
        assertEquals(CopyrightType.TYPE1, byId["2"]!!.copyright)
        assertEquals(CopyrightType.UNSPECIFIED, byId["3"]!!.copyright)
        // 빈 값(UNSPECIFIED)이어도 placeholder가 아니라 실제 출처를 담는다.
        result.spots.forEach {
            assertTrue(it.attributionText.contains("한국관광공사"), "attribution missing: ${it.attributionText}")
        }
    }

    @Test
    fun `거리(distanceMeters)가 서버 계산으로 채워진다 - dist 필드 없이도`() {
        val result = client.parseResponse(ok("""{"item":${item(mapx = "127.0276", mapy = "37.4979")}}"""), query)
        // 서울시청 기준 강남역 좌표 → 0보다 큰 거리.
        assertTrue(result.spots.single().distance.meters > 0.0)
    }

    @Test
    fun `주소는 addr1 addr2를 병합한다`() {
        val result = client.parseResponse(ok("""{"item":${item()}}"""), query)
        assertEquals("서울 종로구", result.spots.single().address)
    }
}
