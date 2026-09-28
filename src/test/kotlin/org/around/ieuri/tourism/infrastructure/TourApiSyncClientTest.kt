package org.around.ieuri.tourism.infrastructure

import org.around.ieuri.tourism.domain.specification.SpotExclusionRule
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiSyncClient
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
        excludeLclssystm2 = listOf("EX999"),
    )

    /** 실제 설정에 들어가는 목록의 축약. 규칙 자체는 SpotExclusionRuleTest가 전량 검사한다. */
    private val exclusionRule = SpotExclusionRule(
        keywords = listOf("약국", "Pharmacy", "CU", "의원"),
        branchNamePatterns = listOf("병원점"),
    )
    private val client = TourApiSyncClient(properties, RestClient.create(), exclusionRule, TourApiCategoryMappingStub())

    private fun item(
        contentId: String? = "100",
        image: String? = "http://tong.visitkorea.or.kr/img/100.jpg",
        title: String? = "경복궁",
        contentTypeId: String? = "12",
        lcls2: String? = null,
        cpyrht: String? = null,
        mapx: Double? = 126.98,
        mapy: Double? = 37.56,
        showflag: String? = "1",
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
        showflag = showflag,
    )

    private fun response(items: List<TourApiResponse.Item>, totalCount: Int = items.size) = TourApiResponse(
        TourApiResponse.Response(
            body = TourApiResponse.Body(
                totalCount = totalCount,
                numOfRows = 1000,
                pageNo = 1,
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

        assertEquals(SpotCategory("SHOPPING"), ko.records.first().category)
        assertEquals(SpotCategory("SHOPPING"), en.records.first().category)
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
    fun `contentTypeId가 없으면 적재하지 않는다`() {
        // 전량 적재 실측(2026-08-03, 121,913건)에서 값이 빠진 항목은 한 건도 없었다. 없으면 카테고리
        // 폴백을 사후 질의할 수단이 사라지므로(V13이 이 컬럼을 둔 이유) 적재 대상에서 제외한다.
        // DB의 NOT NULL(V18)이 이 규율을 쓰기 시점에 한 번 더 막는다.
        val page = client.mapPage(response(listOf(item(contentTypeId = null))), DomainLocale.KO)

        assertTrue(page.records.isEmpty())
        assertEquals(1, page.droppedCount)
    }

    @Test
    fun `contentTypeId가 숫자가 아니면 적재하지 않는다`() {
        // 폴백 대상(해석 불가 코드)과 드랍 대상(코드 자체가 없음)은 다르다 — 9999는 숫자라 원본을
        // 남기고 폴백하지만, 숫자가 아니면 남길 원본이 없다.
        val page = client.mapPage(response(listOf(item(contentTypeId = "알수없음"))), DomainLocale.KO)

        assertTrue(page.records.isEmpty())
        assertEquals(1, page.droppedCount)
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

    // ---------- 비관광 항목 제외 (SPEC-0018) ----------

    @Test
    fun `비관광 키워드에 걸린 항목은 적재하지 않는다`() {
        // 적재하지 않으므로 collectedIds에도 들어가지 않고, 이전에 적재됐던 행은 정리 단계가 지운다.
        val page = client.mapPage(
            response(listOf(item(contentId = "300", title = "명동명약국", contentTypeId = "38"))),
            DomainLocale.KO,
        )

        assertTrue(page.records.isEmpty())
        assertEquals(1, page.excludedCount)
    }

    @Test
    fun `제외 건수는 dropped와 따로 센다`() {
        // 필드 결손(dropped)과 의도된 제외는 성격이 다르다 — 섞으면 키워드 오류가 결손에 묻힌다.
        val page = client.mapPage(
            response(
                listOf(
                    item(contentId = "301", title = "명동명약국", contentTypeId = "38"),
                    item(contentId = "302", image = null),
                    item(contentId = "303", title = "경복궁"),
                ),
            ),
            DomainLocale.KO,
        )

        assertEquals(1, page.records.size)
        assertEquals("303", page.records.first().contentId)
        assertEquals(1, page.excludedCount)
        assertEquals(1, page.droppedCount)
    }

    @Test
    fun `관광 카테고리는 키워드에 걸려도 적재한다`() {
        // 카테고리 해석 이후에 판정하므로 보호 규칙이 성립한다 — 서울 대한의원은 사적이다.
        val page = client.mapPage(
            response(listOf(item(contentId = "304", title = "서울 대한의원", contentTypeId = "12"))),
            DomainLocale.KO,
        )

        assertEquals(1, page.records.size)
        assertEquals(0, page.excludedCount)
    }

    @Test
    fun `단어 안에 든 cu는 제외되지 않는다`() {
        // 경계 없이 CU를 걸면 문화·security까지 잡힌다.
        val page = client.mapPage(
            response(listOf(item(contentId = "305", title = "인사동 문화의 거리", contentTypeId = "38"))),
            DomainLocale.KO,
        )

        assertEquals(1, page.records.size)
        assertEquals(0, page.excludedCount)
    }

    @Test
    fun `키워드 목록이 비면 아무것도 제외하지 않는다`() {
        val permissive = TourApiSyncClient(
            properties,
            RestClient.create(),
            SpotExclusionRule(keywords = emptyList(), branchNamePatterns = emptyList()),
            TourApiCategoryMappingStub(),
        )

        val page = permissive.mapPage(
            response(listOf(item(contentId = "306", title = "명동명약국", contentTypeId = "38"))),
            DomainLocale.KO,
        )

        assertEquals(1, page.records.size)
        assertEquals(0, page.excludedCount)
    }
}
