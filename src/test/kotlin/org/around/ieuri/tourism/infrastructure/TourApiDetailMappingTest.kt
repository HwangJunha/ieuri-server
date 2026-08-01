package org.around.ieuri.tourism.infrastructure

import org.around.ieuri.tourism.application.exception.SpotDetailNotFoundException
import org.around.ieuri.tourism.application.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.tourapi.TourApiClient
import org.around.ieuri.tourism.infrastructure.adapter.out.tourapi.TourApiDetailCommonResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.tourapi.TourApiDetailImageResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.tourapi.TourApiResponse
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TourApiClient 상세 매핑(SPEC-0003) — 2콜 병합·https 정규화(AC-7)·10장 캡(AC-8)·빈 갤러리(AC-9)·
 * 빈문자열 null화(AC-3)·미존재 NotFound(AC-15)·한도초과 RateLimited(AC-16)·오류 Unavailable(AC-17),
 * 그리고 items 단건 객체화/빈문자열 함정 역직렬화(AC-10). HTTP는 배제하고 순수 매핑만 검증한다.
 */
class TourApiDetailMappingTest {

    private val properties = TourApiProperties(
        serviceKey = "dummy",
        baseUrls = mapOf("KO" to "https://ko", "EN" to "https://en"),
        categoryCodes = mapOf(
            "KO" to mapOf(SpotCategory.TOURIST_SPOT to 12, SpotCategory.SHOPPING to 38),
            "FOREIGN" to mapOf(SpotCategory.TOURIST_SPOT to 76),
        ),
        excludeLclssystm2 = emptyList(),
    )
    private val client = TourApiClient(properties, RestClient.create())
    private val mapper = jacksonObjectMapper()

    private fun commonItem(
        contentId: String = "100",
        title: String? = "경복궁",
        overview: String? = "조선의 법궁",
        homepage: String? = "http://royal.go.kr",
        tel: String? = "02-000",
        firstimage: String? = "http://tong.visitkorea.or.kr/100.jpg",
        contentTypeId: String? = "12",
        cpyrht: String? = "Type1",
    ) = TourApiDetailCommonResponse.Item(
        contentid = contentId, title = title, addr1 = "서울", addr2 = "종로구",
        overview = overview, homepage = homepage, tel = tel,
        firstimage = firstimage, contenttypeid = contentTypeId, cpyrhtDivCd = cpyrht,
    )

    private fun imageItem(url: String? = "http://tong.visitkorea.or.kr/g.jpg", cpyrht: String? = "Type3") =
        TourApiDetailImageResponse.Item(originimgurl = url, smallimageurl = "http://tong/small.jpg", imgname = "설명", cpyrhtDivCd = cpyrht)

    // ── mapDetail 조립 ──────────────────────────────────────────────

    @Test
    fun `AC-1 - common+image를 병합해 SpotDetail을 조립한다`() {
        val detail = client.mapDetail(commonItem(), listOf(imageItem(), imageItem()), DomainLocale.KO)
        assertEquals("100", detail.id)
        assertEquals("경복궁", detail.title)
        assertEquals("조선의 법궁", detail.overview)
        assertEquals("http://royal.go.kr", detail.homepage)
        assertEquals(SpotCategory.TOURIST_SPOT, detail.category)
        assertEquals(2, detail.gallery.size)
        assertEquals(CopyrightType.TYPE1, detail.copyright)
        assertEquals(0, detail.likeCount) // 어댑터는 likeCount를 채우지 않는다(§4 경계)
    }

    @Test
    fun `AC-7 - 이미지 URL이 https로 정규화된다`() {
        val detail = client.mapDetail(commonItem(), listOf(imageItem(url = "http://tong/a.jpg")), DomainLocale.KO)
        assertTrue(detail.thumbnailImageUrl.startsWith("https://"))
        assertTrue(detail.gallery.single().imageUrl.startsWith("https://"))
        assertTrue(detail.gallery.single().thumbnailUrl!!.startsWith("https://"))
    }

    @Test
    fun `AC-8 - 갤러리는 반환 순서 앞 10장만 담는다`() {
        val images = (1..15).map { imageItem(url = "http://tong/$it.jpg") }
        val detail = client.mapDetail(commonItem(), images, DomainLocale.KO)
        assertEquals(10, detail.gallery.size)
        assertEquals("https://tong/1.jpg", detail.gallery.first().imageUrl)
        assertEquals("https://tong/10.jpg", detail.gallery.last().imageUrl)
    }

    @Test
    fun `AC-9 - 이미지가 없으면 빈 갤러리(오류 아님), firstimage를 썸네일로 쓴다`() {
        val detail = client.mapDetail(commonItem(), emptyList(), DomainLocale.KO)
        assertTrue(detail.gallery.isEmpty())
        assertEquals("https://tong.visitkorea.or.kr/100.jpg", detail.thumbnailImageUrl)
    }

    @Test
    fun `AC-3 - overview 빈 문자열은 null로 정규화된다`() {
        val detail = client.mapDetail(commonItem(overview = "", homepage = "", tel = ""), emptyList(), DomainLocale.KO)
        assertNull(detail.overview)
        assertNull(detail.homepage)
        assertNull(detail.tel)
    }

    @Test
    fun `firstimage 없으면 갤러리 첫 장을 썸네일로 승격한다`() {
        val detail = client.mapDetail(commonItem(firstimage = null), listOf(imageItem(url = "http://tong/g1.jpg")), DomainLocale.KO)
        assertEquals("https://tong/g1.jpg", detail.thumbnailImageUrl)
    }

    @Test
    fun `firstimage도 갤러리도 없으면 NotFound(썸네일 non-null 계약)`() {
        assertFailsWith<SpotDetailNotFoundException> {
            client.mapDetail(commonItem(firstimage = null), emptyList(), DomainLocale.KO)
        }
    }

    @Test
    fun `원본 url 없는 갤러리 항목은 제외된다`() {
        val detail = client.mapDetail(commonItem(), listOf(imageItem(url = ""), imageItem(url = null), imageItem(url = "http://tong/ok.jpg")), DomainLocale.KO)
        assertEquals(1, detail.gallery.size)
    }

    @Test
    fun `이미지별 저작권이 개별 매핑된다`() {
        val detail = client.mapDetail(commonItem(cpyrht = "Type1"), listOf(imageItem(cpyrht = "Type3")), DomainLocale.KO)
        assertEquals(CopyrightType.TYPE1, detail.copyright)
        assertEquals(CopyrightType.TYPE3, detail.gallery.single().copyright)
    }

    // ── resultCode 판정 ─────────────────────────────────────────────

    private fun commonResponse(items: List<TourApiDetailCommonResponse.Item>, resultCode: String = "0000") =
        TourApiDetailCommonResponse(
            TourApiDetailCommonResponse.Response(
                header = TourApiResponse.Header(resultCode = resultCode, resultMsg = "OK"),
                body = TourApiDetailCommonResponse.Body(items),
            ),
        )

    @Test
    fun `AC-15 - common이 비면 NotFound`() {
        assertFailsWith<SpotDetailNotFoundException> {
            client.mapDetailCommon(commonResponse(emptyList()))
        }
    }

    @Test
    fun `AC-16 - 한도 초과 코드는 RateLimited`() {
        assertFailsWith<UpstreamRateLimitedException> {
            client.mapDetailCommon(commonResponse(emptyList(), resultCode = "22"))
        }
    }

    @Test
    fun `AC-17 - 그 외 비정상 resultCode는 Unavailable`() {
        assertFailsWith<UpstreamUnavailableException> {
            client.mapDetailCommon(commonResponse(emptyList(), resultCode = "99"))
        }
    }

    // ── items 함정 역직렬화(AC-10) ──────────────────────────────────

    @Test
    fun `AC-10 - 단건 이미지가 객체로 와도 1개 배열로 정규화된다`() {
        val json = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
             "body":{"items":{"item":{"originimgurl":"http://tong/single.jpg","cpyrhtDivCd":"Type1"}}}}}
        """.trimIndent()
        val parsed = mapper.readValue(json, TourApiDetailImageResponse::class.java)
        assertEquals(1, parsed.response.body.items.size)
        assertEquals("http://tong/single.jpg", parsed.response.body.items.single().originimgurl)
    }

    @Test
    fun `AC-10 - 이미지 배열은 그대로 역직렬화된다`() {
        val json = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
             "body":{"items":{"item":[{"originimgurl":"http://tong/a.jpg"},{"originimgurl":"http://tong/b.jpg"}]}}}}
        """.trimIndent()
        val parsed = mapper.readValue(json, TourApiDetailImageResponse::class.java)
        assertEquals(2, parsed.response.body.items.size)
    }

    @Test
    fun `결과 없음이면 items가 빈 문자열로 와도 빈 목록으로 역직렬화된다`() {
        val json = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":""}}}
        """.trimIndent()
        val parsed = mapper.readValue(json, TourApiDetailImageResponse::class.java)
        assertTrue(parsed.response.body.items.isEmpty())
    }

    @Test
    fun `common도 단건 객체를 1개 배열로 정규화한다`() {
        val json = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
             "body":{"items":{"item":{"contentid":"100","title":"경복궁","firstimage":"http://tong/100.jpg"}}}}}
        """.trimIndent()
        val parsed = mapper.readValue(json, TourApiDetailCommonResponse::class.java)
        assertEquals("100", parsed.response.body.items.single().contentid)
    }
}
