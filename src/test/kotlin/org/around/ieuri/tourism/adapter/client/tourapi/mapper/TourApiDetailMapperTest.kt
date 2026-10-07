package org.around.ieuri.tourism.adapter.client.tourapi.mapper

import org.around.ieuri.tourism.adapter.client.tourapi.TourApiDetailCommonResponse
import org.around.ieuri.tourism.adapter.client.tourapi.TourApiDetailImageResponse
import org.around.ieuri.tourism.adapter.client.tourapi.TourApiDetailIntroResponse
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** TourApiDetailMapper의 필드별 매핑 검증. 동작 규칙(갤러리 상한·intro 정규화)은 TourApiDetailMappingTest가 덮는다. */
class TourApiDetailMapperTest {

    private fun commonItem(
        title: String? = "불국사",
        addr1: String? = "경상북도 경주시",
        addr2: String? = "불국로 385",
        overview: String? = "신라의 사찰",
        homepage: String? = "https://bulguksa.or.kr",
        tel: String? = "054-746-9913",
        firstimage: String? = "http://tong.visitkorea.or.kr/bulguksa.jpg",
        cpyrhtDivCd: String? = "Type3",
    ) = TourApiDetailCommonResponse.Item(
        contentid = "2001",
        title = title,
        addr1 = addr1,
        addr2 = addr2,
        overview = overview,
        homepage = homepage,
        tel = tel,
        firstimage = firstimage,
        contenttypeid = "12",
        cpyrhtDivCd = cpyrhtDivCd,
    )

    // ── toSpotDetail ────────────────────────────────────────────────

    @Test
    fun `모든 필드가 채워진 item을 SpotDetail로 옮긴다`() {
        val detail = commonItem().toSpotDetail(SpotCategory("SHOPPING"))

        assertEquals("불국사", detail.title)
        assertEquals(SpotCategory("SHOPPING"), detail.category)
        assertEquals("https://tong.visitkorea.or.kr/bulguksa.jpg", detail.thumbnailImageUrl)
        assertEquals("신라의 사찰", detail.overview)
        assertEquals("경상북도 경주시 불국로 385", detail.address)
        assertEquals("https://bulguksa.or.kr", detail.homepage)
        assertEquals("054-746-9913", detail.tel)
        assertEquals(CopyrightType.TYPE3, detail.copyright)
        assertEquals(0, detail.likeCount)
    }

    @Test
    fun `nullable 필드가 모두 null이어도 예외 없이 옮긴다`() {
        val detail = commonItem(
            overview = null,
            addr1 = null,
            addr2 = null,
            homepage = null,
            tel = null,
            cpyrhtDivCd = null,
        ).toSpotDetail(SpotCategory.TOURIST_SPOT)

        assertNull(detail.overview)
        assertNull(detail.address)
        assertNull(detail.homepage)
        assertNull(detail.tel)
        assertEquals(CopyrightType.UNSPECIFIED, detail.copyright)
        assertEquals("불국사", detail.title)
    }

    @Test
    fun `주소가 한쪽만 있으면 그 값만 담는다`() {
        assertEquals("경상북도 경주시", commonItem(addr2 = null).toSpotDetail(SpotCategory.TOURIST_SPOT).address)
        assertEquals("불국로 385", commonItem(addr1 = null).toSpotDetail(SpotCategory.TOURIST_SPOT).address)
    }

    // ── toSpotImage ─────────────────────────────────────────────────

    @Test
    fun `모든 필드가 채워진 이미지 item을 SpotImage로 옮긴다`() {
        val image = TourApiDetailImageResponse.Item(
            originimgurl = "http://tong.visitkorea.or.kr/origin.jpg",
            smallimageurl = "http://tong.visitkorea.or.kr/small.jpg",
            imgname = "대웅전 전경",
            cpyrhtDivCd = "Type1",
        ).toSpotImage()

        assertEquals("https://tong.visitkorea.or.kr/origin.jpg", image.imageUrl)
        assertEquals("https://tong.visitkorea.or.kr/small.jpg", image.thumbnailUrl)
        assertEquals("대웅전 전경", image.caption)
        assertEquals(CopyrightType.TYPE1, image.copyright)
    }

    @Test
    fun `이미지의 nullable 필드가 모두 null이어도 예외 없이 옮긴다`() {
        val image = TourApiDetailImageResponse.Item(
            originimgurl = "https://tong.visitkorea.or.kr/origin.jpg",
            smallimageurl = null,
            imgname = null,
            cpyrhtDivCd = null,
        ).toSpotImage()

        assertNull(image.thumbnailUrl)
        assertNull(image.caption)
        assertEquals(CopyrightType.UNSPECIFIED, image.copyright)
        assertEquals("https://tong.visitkorea.or.kr/origin.jpg", image.imageUrl)
    }

    // ── toSpotIntroOrNull ───────────────────────────────────────────

    @Test
    fun `모든 공통 필드가 채워진 intro item을 SpotIntro로 옮긴다`() {
        // 필드마다 값을 달리 줘 뒤바뀐 매핑이 드러나게 한다.
        val intro = assertNotNull(
            TourApiDetailIntroResponse.Item(
                usetime = "09:00~17:00",
                restdate = "매주 월요일",
                parking = "주차 가능",
                infocenter = "054-111-2222",
                usefee = "성인 6000원",
                chkpet = "동반 불가",
                chkcreditcard = "카드 가능",
            ).toSpotIntroOrNull(),
        )

        assertEquals("09:00~17:00", intro.businessHours)
        assertEquals("매주 월요일", intro.closedDays)
        assertEquals("주차 가능", intro.parking)
        assertEquals("054-111-2222", intro.inquiryPhone)
        assertEquals("성인 6000원", intro.fee)
        assertEquals("동반 불가", intro.petAllowed)
        assertEquals("카드 가능", intro.creditCardAccepted)
    }

    @Test
    fun `일부 필드만 있으면 나머지 도메인 필드는 null이다`() {
        val intro = assertNotNull(
            TourApiDetailIntroResponse.Item(usefeeleports = "1인 20000원").toSpotIntroOrNull(),
        )

        assertEquals("1인 20000원", intro.fee)
        assertNull(intro.businessHours)
        assertNull(intro.closedDays)
        assertNull(intro.parking)
        assertNull(intro.inquiryPhone)
        assertNull(intro.petAllowed)
        assertNull(intro.creditCardAccepted)
    }

    @Test
    fun `모든 필드가 null이면 SpotIntro 자체가 null이다`() {
        assertNull(TourApiDetailIntroResponse.Item().toSpotIntroOrNull())
    }
}
