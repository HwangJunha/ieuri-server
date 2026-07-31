package org.around.ieuri.tourism.infrastructure

import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.tourapi.TourApiClient
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * TourApiClient 파싱·매핑 — 응답 역직렬화, resultCode 판정(AC-7), 비관광 필터(AC-4)·이미지 https(AC-3)·
 * 저작권 매핑(AC-15). HTTP를 배제하고 parseAndMap만 검증한다.
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
    // 실제 앱은 Boot가 KotlinModule 등록한 ObjectMapper를 주입한다. 테스트도 동일하게 kotlin 모듈을 등록.
    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val client = TourApiClient(properties, objectMapper, RestClient.create())

    private fun ok(itemsJson: String, totalCount: Int = 1) = """
        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
        "body":{"numOfRows":100,"pageNo":1,"totalCount":$totalCount,"items":{"item":$itemsJson}}}}
    """.trimIndent()

    private fun item(
        contentId: String = "100",
        image: String = "http://tong.visitkorea.or.kr/img/100.jpg",
        mapx: String = "126.98",
        mapy: String = "37.56",
        title: String = "경복궁",
        contentTypeId: String = "12",
        lcls2: String? = null,
        cpyrht: String? = null,
    ): String {
        val lcls = lcls2?.let { ""","lclsSystm2":"$it"""" } ?: ""
        val cpy = cpyrht?.let { ""","cpyrhtDivCd":"$it"""" } ?: ""
        return """{"contentid":"$contentId","firstimage":"$image","mapx":$mapx,"mapy":$mapy,"title":"$title","addr1":"서울","addr2":"종로구","contenttypeid":"$contentTypeId"$lcls$cpy}"""
    }

    @Test
    fun `여러 건 item 배열을 매핑한다`() {
        val result = client.parseAndMap(ok("""[${item("1")},${item("2")}]""", totalCount = 2), DomainLocale.KO)
        assertEquals(listOf("1", "2"), result.spots.map { it.id })
    }

    @Test
    fun `미정의 필드가 있어도 무시하고 파싱한다`() {
        val weird = """[{"contentid":"7","firstimage":"http://x/7.jpg","mapx":126.9,"mapy":37.5,"title":"T","contenttypeid":"12","unknownField":"boom"}]"""
        val result = client.parseAndMap(ok(weird), DomainLocale.KO)
        assertEquals("7", result.spots.single().id)
    }

    @Test
    fun `resultCode가 0000이 아니면 UpstreamUnavailable(에러도 JSON)`() {
        val err = """{"response":{"header":{"resultCode":"99","resultMsg":"UNKNOWN_ERROR"},"body":{}}}"""
        assertFailsWith<UpstreamUnavailableException> { client.parseAndMap(err, DomainLocale.KO) }
    }

    @Test
    fun `AC-3 - 이미지 URL이 https로 정규화된다`() {
        val result = client.parseAndMap(ok("""[${item(image = "http://tong.visitkorea.or.kr/a.jpg")}]"""), DomainLocale.KO)
        assertTrue(result.spots.single().thumbnailImageUrl.startsWith("https://"))
    }

    @Test
    fun `AC-3 - 이미지가 없는 항목은 제외된다`() {
        val noImage = """[{"contentid":"9","firstimage":"","mapx":126.9,"mapy":37.5,"title":"T","contenttypeid":"12"}]"""
        val result = client.parseAndMap(ok(noImage), DomainLocale.KO)
        assertTrue(result.spots.isEmpty())
    }

    @Test
    fun `AC-4 - 비관광 lclsSystm2 코드 항목은 필터링된다`() {
        val json = ok("""[${item("1", lcls2 = "EX999")},${item("2", lcls2 = "OK001")}]""", totalCount = 2)
        val result = client.parseAndMap(json, DomainLocale.KO)
        assertEquals(listOf("2"), result.spots.map { it.id })
    }

    @Test
    fun `AC-15 - cpyrhtDivCd를 CopyrightType으로 매핑하고 빈 값도 UNSPECIFIED`() {
        val json = ok("""[${item("1", cpyrht = "Type3")},${item("2", cpyrht = "Type1")},${item("3")}]""", totalCount = 3)
        val byId = client.parseAndMap(json, DomainLocale.KO).spots.associateBy { it.id }
        assertEquals(CopyrightType.TYPE3, byId["1"]!!.copyright)
        assertEquals(CopyrightType.TYPE1, byId["2"]!!.copyright)
        assertEquals(CopyrightType.UNSPECIFIED, byId["3"]!!.copyright)
    }

    @Test
    fun `contenttypeid를 도메인 카테고리로 역매핑한다`() {
        val result = client.parseAndMap(ok("""[${item(contentTypeId = "38")}]"""), DomainLocale.KO) // KO 38=SHOPPING
        assertEquals(SpotCategory.SHOPPING, result.spots.single().category)
    }

    @Test
    fun `주소는 addr1 addr2를 병합한다`() {
        val result = client.parseAndMap(ok("""[${item()}]"""), DomainLocale.KO)
        assertEquals("서울 종로구", result.spots.single().address)
    }

    @Test
    fun `0건이면 빈 리스트`() {
        val result = client.parseAndMap(ok("[]", totalCount = 0), DomainLocale.KO)
        assertTrue(result.spots.isEmpty())
    }
}
