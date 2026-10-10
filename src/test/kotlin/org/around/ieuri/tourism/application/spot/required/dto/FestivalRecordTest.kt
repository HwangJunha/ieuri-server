package org.around.ieuri.tourism.application.spot.required.dto

import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** 축제 목록 한 행의 조립 규칙(SPEC-0007 R-1). 제목·주소·좌표는 원본의 몫이라 담지 않는다(AC-3). */
class FestivalRecordTest {

    private val period = FestivalPeriod(
        start = LocalDate.of(2026, 8, 1),
        end = LocalDate.of(2026, 8, 10),
    )

    private fun record(
        contentId: String = "3113671",
        language: DomainLocale = DomainLocale.KO,
    ) = FestivalRecord(
        contentId = contentId,
        language = language,
        period = period,
        progressType = "선택안함",
        festivalType = null,
    )

    @Test
    fun `축제 전용 값이 그대로 담긴다`() {
        val record = record()

        assertEquals("3113671", record.contentId)
        assertEquals(DomainLocale.KO, record.language)
        assertEquals(LocalDate.of(2026, 8, 1), record.period.start)
        assertEquals(LocalDate.of(2026, 8, 10), record.period.end)
        assertEquals("선택안함", record.progressType)
        assertEquals(null, record.festivalType)
    }

    @Test
    fun `contentId가 비면 조립이 거부된다`() {
        // AC-4. 원본을 찾을 수 없는 행은 들이지 않는다.
        assertFailsWith<IllegalArgumentException> { record(contentId = "") }
        assertFailsWith<IllegalArgumentException> { record(contentId = "   ") }
    }

    @Test
    fun `같은 contentId라도 언어가 다르면 서로 다른 행이다`() {
        // AC-2. 언어가 키의 일부다.
        assertNotEquals(record(language = DomainLocale.KO), record(language = DomainLocale.EN))
    }
}
