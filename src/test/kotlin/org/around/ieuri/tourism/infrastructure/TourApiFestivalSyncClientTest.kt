package org.around.ieuri.tourism.infrastructure

import io.mockk.mockk
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.tourapi.TourApiFestivalResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.tourapi.TourApiFestivalSyncClient
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.springframework.web.client.RestClient
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * searchFestival2 응답 → 적재 레코드 매핑(SPEC-0007 R-3·R-4·R-5).
 *
 * `mapPage`는 HTTP·역직렬화와 분리되어 있으므로 응답 객체를 직접 만들어 호출한다 — RestClient를
 * 띄우지 않는다(`TourApiSyncClientTest`와 같은 관례).
 */
class TourApiFestivalSyncClientTest {

    private val runDate = LocalDate.of(2026, 8, 3)

    private val client = TourApiFestivalSyncClient(
        properties = mockk<TourApiProperties>(relaxed = true),
        tourApiRestClient = mockk<RestClient>(),
    )

    private fun item(
        contentId: String? = "3113671",
        start: String? = "20260801",
        end: String? = "20260810",
        progressType: String? = "선택안함",
        festivalType: String? = "선택안함",
    ) = TourApiFestivalResponse.Item(
        contentid = contentId,
        eventstartdate = start,
        eventenddate = end,
        progresstype = progressType,
        festivaltype = festivalType,
    )

    private fun response(vararg items: TourApiFestivalResponse.Item) = TourApiFestivalResponse(
        response = TourApiFestivalResponse.Response(
            body = TourApiFestivalResponse.Body(
                totalCount = items.size,
                items = TourApiFestivalResponse.Items(item = items.toList()),
            ),
        ),
    )

    private fun map(vararg items: TourApiFestivalResponse.Item) =
        client.mapPage(response(*items), DomainLocale.KO, runDate)

    // ---------- 날짜 해석 (R-4) ----------

    @Test
    fun `YYYYMMDD 시작일과 종료일을 날짜로 해석한다`() {
        // AC-12.
        val page = map(item(start = "20260801", end = "20260810"))

        val record = page.records.single()
        assertEquals(LocalDate.of(2026, 8, 1), record.period.start)
        assertEquals(LocalDate.of(2026, 8, 10), record.period.end)
    }

    @Test
    fun `시작일이 빈 문자열이면 드랍한다`() {
        // AC-13. 날짜를 해석할 수 없으면 유효성 판정도 정리도 불가능하다.
        val page = map(item(start = ""))

        assertTrue(page.records.isEmpty())
        assertEquals(1, page.droppedCount)
    }

    @Test
    fun `종료일이 YYYYMMDD가 아닌 형식이면 드랍한다`() {
        // AC-14. 관대하게 보정하면 잘못 해석된 종료일로 정리 판정이 조용히 틀린다.
        val page = map(item(end = "2026-08-10"))

        assertTrue(page.records.isEmpty())
        assertEquals(1, page.droppedCount)
    }

    @Test
    fun `종료일이 시작일보다 이전이면 드랍한다`() {
        // AC-15. 도메인이 던지는 예외를 항목 드랍으로 흡수한다 — 페이지 전체를 실패시키지 않는다.
        val page = map(item(start = "20260810", end = "20260801"))

        assertTrue(page.records.isEmpty())
    }

    @Test
    fun `연말연시 행사는 연도를 넘겨 정상 해석된다`() {
        // AC-16. 실행일을 그 기간 안에 둔다 — 기간 밖이면 AC-9의 종료 판정에 먼저 걸려
        // "연도를 넘겨 해석되는가"를 검증할 수 없다.
        val page = client.mapPage(
            response(item(start = "20251219", end = "20260103")),
            DomainLocale.KO,
            LocalDate.of(2025, 12, 31),
        )

        val record = page.records.single()
        assertEquals(LocalDate.of(2025, 12, 19), record.period.start)
        assertEquals(LocalDate.of(2026, 1, 3), record.period.end)
    }

    @Test
    fun `contentId가 비면 드랍한다`() {
        // AC-4. spot의 어느 행에도 연결할 수 없다.
        val page = map(item(contentId = " "))

        assertTrue(page.records.isEmpty())
    }

    // ---------- 실행일 기준 유효성 (R-3) ----------

    @Test
    fun `종료일이 실행일보다 이전이면 적재 대상에서 제외한다`() {
        // AC-9. 업스트림이 유효한 것만 준다는 보장에만 기대지 않는다.
        val page = map(item(start = "20260701", end = "20260802"))

        assertTrue(page.records.isEmpty())
        assertEquals(1, page.droppedCount)
    }

    @Test
    fun `시작일이 미래인 축제는 적재 대상에 포함한다`() {
        // AC-10. eventStartDate는 "그 날짜 기준 유효한 행사"를 뜻하므로 예정된 행사도 유효하다.
        val page = map(item(start = "20260901", end = "20260910"))

        assertEquals(1, page.records.size)
    }

    @Test
    fun `실행일 당일에 시작하고 끝나는 행사는 적재한다`() {
        // AC-11. 종료일 == 실행일은 아직 종료가 아니다.
        val page = map(item(start = "20260803", end = "20260803"))

        assertEquals(1, page.records.size)
    }

    // ---------- 진행형태·축제형태 원문 보존 (R-5) ----------

    @Test
    fun `진행형태 문자열을 변환 없이 그대로 담는다`() {
        // AC-17.
        val page = map(item(progressType = "선택안함"))

        assertEquals("선택안함", page.records.single().progressType)
    }

    @Test
    fun `축제형태가 빈 문자열이면 값 없는 상태로 적재한다`() {
        // AC-18. 빈 문자열과 "값 없음"을 구분하지 않는다 — 둘 다 의미가 없다.
        val page = map(item(festivalType = ""))

        assertNull(page.records.single().festivalType)
    }

    @Test
    fun `알려지지 않은 임의의 값도 매핑에 실패하지 않고 보존된다`() {
        // AC-19. 공식 코드표가 없으므로 어떤 값이 와도 해석하지 않는다([TBD-1]).
        val page = map(item(progressType = "미확인신규코드"))

        assertEquals("미확인신규코드", page.records.single().progressType)
    }

    // ---------- 관측 (R-11) ----------

    @Test
    fun `드랍 건수를 적재 건수와 구분해 남긴다`() {
        // AC-41. [TBD-2]는 이 값으로만 해소된다.
        val page = map(
            item(contentId = "1"),
            item(contentId = "2", start = ""),
            item(contentId = "3", end = "잘못된값"),
        )

        assertEquals(1, page.records.size)
        assertEquals(2, page.droppedCount)
    }
}
