package org.around.ieuri.tourism.infrastructure

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.tourapi.TourApiResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.tourapi.TourApiSyncClient
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * areaBasedSyncList2 수집 매핑(SPEC-0004). HTTP·역직렬화는 라이브러리 책임이라 배제하고
 * mapPage(DTO→적재 레코드)만 검증한다(TourApiClientTest와 같은 관례).
 *
 * 핵심은 **적재 레코드가 조회 계약의 불변식을 쓰기 시점에 만족하는가**다 — thumbnailImageUrl의
 * non-null·https 보장이 요청 시점 필터에서 이 매핑으로 옮겨왔기 때문이다(런타임 계약 위험 1번).
 */
class TourApiSyncClientTest {

    private val properties = TourApiProperties(
        serviceKey = "dummy",
        baseUrls = mapOf("KO" to "https://ko", "EN" to "https://en"),
        categoryCodes = mapOf(
            "KO" to mapOf(SpotCategory.SHOPPING to 38, SpotCategory.TOURIST_SPOT to 12),
            "FOREIGN" to mapOf(SpotCategory.SHOPPING to 79, SpotCategory.TOURIST_SPOT to 76),
        ),
        excludeLclssystm2 = listOf("EX999"),
    )
    private val client = TourApiSyncClient(properties, RestClient.create())

    private fun item(
        contentId: String? = "100",
        image: String? = "http://tong.visitkorea.or.kr/img/100.jpg",
        title: String? = "경복궁",
        contentTypeId: String? = "12",
        lcls2: String? = null,
        cpyrht: String? = null,
        mapx: Double? = 126.98,
        mapy: Double? = 37.56,
    ) = TourApiResponse.Item(
        contentid = contentId,
        title = title,
        addr1 = "서울",
        addr2 = "종로구",
        firstimage = image,
        mapx = mapx,
        mapy = mapy,
        contenttypeid = contentTypeId,
        lclsSystm2 = lcls2,
        cpyrhtDivCd = cpyrht,
    )

    private fun response(items: List<TourApiResponse.Item>, totalCount: Int = items.size) =
        TourApiResponse(
            TourApiResponse.Response(
                body = TourApiResponse.Body(
                    totalCount = totalCount, numOfRows = 1000, pageNo = 1,
                    items = TourApiResponse.Items(item = items),
                ),
            ),
        )

    @Test
    fun `이미지 URL을 https로 정규화해 적재한다`() {
        val page = client.mapPage(response(listOf(item())), DomainLocale.KO)

        assertEquals(1, page.records.size)
        assertEquals("https://tong.visitkorea.or.kr/img/100.jpg", page.records.first().thumbnailImageUrl)
    }

    @Test
    fun `대표이미지가 없는 항목은 적재하지 않는다`() {
        // arrange=Q가 upstream에서 걸러 주지만 그 보장에만 기대지 않는다 — SDL이 non-null이다.
        val page = client.mapPage(response(listOf(item(image = null), item(contentId = "200"))), DomainLocale.KO)

        assertEquals(1, page.records.size)
        assertEquals("200", page.records.first().contentId)
        assertEquals(1, page.droppedCount)
    }

    @Test
    fun `contentId나 title이 비면 적재하지 않는다`() {
        val page = client.mapPage(
            response(listOf(item(contentId = null), item(contentId = "201", title = null))),
            DomainLocale.KO,
        )

        assertTrue(page.records.isEmpty())
        assertEquals(2, page.droppedCount)
    }

    @Test
    fun `좌표 범위를 벗어난 항목은 배치를 중단시키지 않고 드랍된다`() {
        // 한 건의 이상 좌표가 페이지 전체를 실패시킬 이유가 없다.
        val page = client.mapPage(
            response(listOf(item(mapy = 999.0), item(contentId = "202"))),
            DomainLocale.KO,
        )

        assertEquals(1, page.records.size)
        assertEquals("202", page.records.first().contentId)
    }

    @Test
    fun `언어별 코드셋으로 카테고리를 해석한다`() {
        val ko = client.mapPage(response(listOf(item(contentTypeId = "38"))), DomainLocale.KO)
        val en = client.mapPage(response(listOf(item(contentTypeId = "79"))), DomainLocale.EN)

        assertEquals(SpotCategory.SHOPPING, ko.records.first().category)
        assertEquals(SpotCategory.SHOPPING, en.records.first().category)
    }

    @Test
    fun `해석 불가 코드는 폴백하되 폴백 건수와 원본 코드를 남긴다`() {
        // 요청 단위에서 간헐적이던 폴백이 배치에서는 대량 적용될 수 있어 관측이 필요하다
        // (런타임 계약 위험 4번). 원본 코드를 적재해야 사후 교정도 가능하다.
        val page = client.mapPage(response(listOf(item(contentTypeId = "9999"))), DomainLocale.KO)

        val record = page.records.first()
        assertEquals(SpotCategory.TOURIST_SPOT, record.category)
        assertEquals(9999, record.contentTypeId)
        assertEquals(1, page.categoryFallbackCount)
    }

    @Test
    fun `정상 해석된 항목은 폴백으로 세지 않는다`() {
        val page = client.mapPage(response(listOf(item(contentTypeId = "38"))), DomainLocale.KO)

        assertEquals(0, page.categoryFallbackCount)
    }

    @Test
    fun `제외 대상 lclsSystm2 항목은 적재하지 않는다`() {
        val page = client.mapPage(response(listOf(item(lcls2 = "EX999"))), DomainLocale.KO)

        assertTrue(page.records.isEmpty())
    }

    @Test
    fun `주소는 addr1과 addr2를 병합한다`() {
        val page = client.mapPage(response(listOf(item())), DomainLocale.KO)

        assertEquals("서울 종로구", page.records.first().address)
    }

    @Test
    fun `저작권 코드를 도메인 타입으로 매핑한다`() {
        val unspecified = client.mapPage(response(listOf(item(cpyrht = null))), DomainLocale.KO)

        assertEquals(CopyrightType.UNSPECIFIED, unspecified.records.first().copyright)
    }

    @Test
    fun `다음 페이지 존재 여부는 totalCount로 판정한다`() {
        val page = client.mapPage(response(listOf(item()), totalCount = 2_500), DomainLocale.KO)

        assertTrue(page.hasMore(pageNo = 1, numOfRows = 1_000))
        assertTrue(page.hasMore(pageNo = 2, numOfRows = 1_000))
        // 3페이지까지면 3000 >= 2500이라 소진.
        assertTrue(!page.hasMore(pageNo = 3, numOfRows = 1_000))
    }
}
