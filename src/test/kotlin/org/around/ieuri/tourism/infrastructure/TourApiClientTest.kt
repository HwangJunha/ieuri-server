package org.around.ieuri.tourism.infrastructure

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiClient
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiResponse
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TourApiClient 매핑 — 이미지 https 정규화(AC-3)·저작권 매핑(AC-15)·코드 역매핑·주소 병합. mapResponse만 검증한다. */
class TourApiClientTest {

    private val properties = TourApiProperties(
        serviceKey = "dummy",
        baseUrls = mapOf("KO" to "https://ko", "EN" to "https://en"),
        excludeLclssystm2 = listOf("EX999"),
    )
    private val client = TourApiClient(properties, RestClient.create(), TourApiCategoryMappingStub())

    private fun item(
        contentId: String = "100",
        image: String? = "http://tong.visitkorea.or.kr/img/100.jpg",
        title: String? = "경복궁",
        contentTypeId: String = "12",
        lcls2: String? = null,
        cpyrht: String? = null,
    ) = TourApiResponse.Item(
        contentid = contentId,
        title = title,
        addr1 = "서울",
        addr2 = "종로구",
        firstimage = image,
        mapx = 126.98,
        mapy = 37.56,
        contenttypeid = contentTypeId,
        lclsSystm2 = lcls2,
        cpyrhtDivCd = cpyrht,
    )

    private fun response(items: List<TourApiResponse.Item>, totalCount: Int = items.size, resultCode: String = "0000") = TourApiResponse(
        TourApiResponse.Response(
            header = TourApiResponse.Header(resultCode = resultCode, resultMsg = "OK"),
            body = TourApiResponse.Body(
                totalCount = totalCount,
                numOfRows = 100,
                pageNo = 1,
                items = TourApiResponse.Items(item = items),
            ),
        ),
    )

    @Test
    fun `여러 건 item을 매핑한다`() {
        val result = client.mapResponse(response(listOf(item("1"), item("2"))), DomainLocale.KO)
        assertEquals(listOf("1", "2"), result.spots.map { it.id })
    }

    @Test
    fun `AC-3 - 이미지 URL이 https로 정규화된다`() {
        val result = client.mapResponse(response(listOf(item(image = "http://tong.visitkorea.or.kr/a.jpg"))), DomainLocale.KO)
        assertTrue(result.spots.single().thumbnailImageUrl.startsWith("https://"))
    }

    @Test
    fun `AC-15 - cpyrhtDivCd를 CopyrightType으로 매핑하고 빈 값도 UNSPECIFIED`() {
        val byId = client.mapResponse(
            response(listOf(item("1", cpyrht = "Type3"), item("2", cpyrht = "Type1"), item("3"))),
            DomainLocale.KO,
        ).spots.associateBy { it.id }
        assertEquals(CopyrightType.TYPE3, byId["1"]!!.copyright)
        assertEquals(CopyrightType.TYPE1, byId["2"]!!.copyright)
        assertEquals(CopyrightType.UNSPECIFIED, byId["3"]!!.copyright)
    }

    @Test
    fun `contenttypeid를 도메인 카테고리로 역매핑한다`() {
        val result = client.mapResponse(response(listOf(item(contentTypeId = "38"))), DomainLocale.KO) // KO 38=SHOPPING
        assertEquals(SpotCategory("SHOPPING"), result.spots.single().category)
    }

    @Test
    fun `주소는 addr1 addr2를 병합한다`() {
        val result = client.mapResponse(response(listOf(item())), DomainLocale.KO)
        assertEquals("서울 종로구", result.spots.single().address)
    }

    @Test
    fun `0건이면 빈 리스트`() {
        val result = client.mapResponse(response(emptyList(), totalCount = 0), DomainLocale.KO)
        assertTrue(result.spots.isEmpty())
    }
}
