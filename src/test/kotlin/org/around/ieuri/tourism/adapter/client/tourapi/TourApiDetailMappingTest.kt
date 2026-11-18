package org.around.ieuri.tourism.adapter.client.tourapi

import org.around.ieuri.tourism.adapter.config.TourApiProperties
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TourApiClient 상세 매핑(SPEC-0003) — https 정규화(AC-7)·10장 캡(AC-8)·빈 갤러리(AC-9)·저작권·items 역직렬화(AC-10). */
class TourApiDetailMappingTest {

    private val properties = TourApiProperties(
        serviceKey = "dummy",
        baseUrls = mapOf("KO" to "https://ko", "EN" to "https://en"),
        excludeLclssystm2 = emptyList(),
    )
    private val client = TourApiClient(properties, RestClient.create(), TourApiCategoryMappingStub())
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

    private fun imageItem(url: String? = "http://tong.visitkorea.or.kr/g.jpg", cpyrht: String? = "Type3") = TourApiDetailImageResponse.Item(originimgurl = url, smallimageurl = "http://tong/small.jpg", imgname = "설명", cpyrhtDivCd = cpyrht)

    // ── mapDetail: 본문(detailCommon2) ──────────────────────────────

    @Test
    fun `AC-1 - detailCommon2 item을 SpotDetail 본문으로 매핑한다`() {
        val detail = client.mapDetail(commonItem(), DomainLocale.KO)
        // 식별자는 이 모델에 없다(V20) — 읽기 DTO가 요청받은 값으로 채운다.
        assertEquals("경복궁", detail.title)
        assertEquals("조선의 법궁", detail.overview)
        assertEquals("http://royal.go.kr", detail.homepage)
        assertEquals("서울 종로구", detail.address)
        assertEquals(SpotCategory.TOURIST_SPOT, detail.category)
        assertEquals(CopyrightType.TYPE1, detail.copyright)
    }

    @Test
    fun `AC-7 - 썸네일 URL이 https로 정규화된다`() {
        val detail = client.mapDetail(commonItem(firstimage = "http://tong/a.jpg"), DomainLocale.KO)
        assertTrue(detail.thumbnailImageUrl.startsWith("https://"))
    }

    // ── mapGallery(detailImage2) ────────────────────────────────────

    @Test
    fun `AC-7 - 갤러리 이미지 URL이 https로 정규화된다`() {
        val gallery = client.mapGallery(listOf(imageItem(url = "http://tong/a.jpg")))
        assertTrue(gallery.single().imageUrl.startsWith("https://"))
        assertTrue(gallery.single().thumbnailUrl!!.startsWith("https://"))
    }

    @Test
    fun `AC-8 - 갤러리는 반환 순서 앞 10장만 담는다`() {
        val gallery = client.mapGallery((1..15).map { imageItem(url = "http://tong/$it.jpg") })
        assertEquals(10, gallery.size)
        assertEquals("https://tong/1.jpg", gallery.first().imageUrl)
        assertEquals("https://tong/10.jpg", gallery.last().imageUrl)
    }

    @Test
    fun `AC-9 - 이미지가 없으면 빈 갤러리(오류 아님)`() {
        assertTrue(client.mapGallery(emptyList()).isEmpty())
    }

    @Test
    fun `이미지별 저작권이 개별 매핑된다`() {
        assertEquals(CopyrightType.TYPE3, client.mapGallery(listOf(imageItem(cpyrht = "Type3"))).single().copyright)
    }

    // ── mapIntro: 카테고리별 필드 → 공통 필드 정규화 ──────────────────

    @Test
    fun `관광지 intro 필드를 공통 이름으로 정규화한다`() {
        val intro = client.mapIntro(
            TourApiDetailIntroResponse.Item(
                usetime = "09:00~18:00",
                restdate = "화요일",
                parking = "가능(무료)",
                infocenter = "02-1234",
                chkpet = "불가",
                chkcreditcard = "가능",
            ),
        )

        assertEquals("09:00~18:00", intro?.businessHours)
        assertEquals("화요일", intro?.closedDays)
        assertEquals("가능(무료)", intro?.parking)
        assertEquals("02-1234", intro?.inquiryPhone)
        assertEquals("불가", intro?.petAllowed)
        assertEquals("가능", intro?.creditCardAccepted)
    }

    @Test
    fun `음식점 intro 필드도 같은 공통 이름으로 정규화된다`() {
        // 음식점은 필드명이 다르다(opentimefood/restdatefood/parkingfood…).
        val intro = client.mapIntro(
            TourApiDetailIntroResponse.Item(
                opentimefood = "11:00~22:00",
                restdatefood = "연중무휴",
                parkingfood = "주차장 있음",
                infocenterfood = "02-5678",
                chkcreditcardfood = "가능",
            ),
        )

        assertEquals("11:00~22:00", intro?.businessHours)
        assertEquals("연중무휴", intro?.closedDays)
        assertEquals("주차장 있음", intro?.parking)
        assertEquals("02-5678", intro?.inquiryPhone)
    }

    @Test
    fun `intro에 값이 하나도 없으면 null이다(빈 껍데기 방지)`() {
        assertNull(client.mapIntro(TourApiDetailIntroResponse.Item(usetime = "", parking = null)))
    }

    @Test
    fun `intro 응답 item이 없으면 null이다`() {
        assertNull(client.mapIntro(null))
    }

    // ── mapInfoItems(detailInfo2) ───────────────────────────────────

    @Test
    fun `이용안내 항목을 name·text 쌍으로 매핑한다`() {
        val items = client.mapInfoItems(
            listOf(
                TourApiDetailInfoResponse.Item(infoname = "입장료", infotext = "성인 3,000원"),
                TourApiDetailInfoResponse.Item(infoname = "이용시간", infotext = "09:00~18:00"),
            ),
        )

        assertEquals(listOf("입장료", "이용시간"), items.map { it.name })
        assertEquals("성인 3,000원", items.first().text)
    }

    @Test
    fun `항목명이나 내용이 비면 그 항목은 제외된다`() {
        val items = client.mapInfoItems(
            listOf(
                TourApiDetailInfoResponse.Item(infoname = "정상", infotext = "값"),
                TourApiDetailInfoResponse.Item(infoname = "", infotext = "값"),
                TourApiDetailInfoResponse.Item(infoname = "이름만", infotext = null),
            ),
        )
        assertEquals(1, items.size)
    }

    @Test
    fun `이용안내가 없으면 빈 목록이다(오류 아님)`() {
        assertTrue(client.mapInfoItems(emptyList()).isEmpty())
    }

    /** 본문이 없으면 `items`가 빈 문자열로 와서 빈 리스트가 된다 — `findDetail`은 null을 돌려준다(SPEC-0008 R-1). */
    @Test
    fun `본문이 없으면 common items가 빈 목록으로 역직렬화된다(SPEC-0008 R-1)`() {
        val json = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":""}}}
        """.trimIndent()
        val parsed = mapper.readValue(json, TourApiDetailCommonResponse::class.java)

        assertTrue(parsed.response.body.items.isEmpty())
        // 어댑터는 firstOrNull()로 받아 null을 돌려준다.
        assertNull(parsed.response.body.items.firstOrNull())
    }

    @Test
    fun `intro items가 빈 문자열로 와도 빈 목록으로 역직렬화된다`() {
        val json = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":""}}}
        """.trimIndent()
        assertTrue(mapper.readValue(json, TourApiDetailIntroResponse::class.java).response.body.items.isEmpty())
    }

    @Test
    fun `info 단건 객체도 1개 배열로 정규화된다`() {
        val json = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
             "body":{"items":{"item":{"infoname":"입장료","infotext":"무료"}}}}}
        """.trimIndent()
        val parsed = mapper.readValue(json, TourApiDetailInfoResponse::class.java)
        assertEquals("입장료", parsed.response.body.items.single().infoname)
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
