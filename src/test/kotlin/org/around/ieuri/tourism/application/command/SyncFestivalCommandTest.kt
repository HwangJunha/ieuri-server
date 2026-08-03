package org.around.ieuri.tourism.application.command

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.FestivalPeriod
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * 축제 적재 입력의 조립 규칙(SPEC-0007 R-1).
 *
 * 이 커맨드가 **무엇을 담지 않는지**가 중요하다 — 제목·주소·좌표는 `spot`에 이미 있고, 축제 적재가
 * 그 값을 다시 쓰면 두 수집 경로가 같은 컬럼을 놓고 경합한다(AC-3).
 */
class SyncFestivalCommandTest {

    private val period = FestivalPeriod(
        start = LocalDate.of(2026, 8, 1),
        end = LocalDate.of(2026, 8, 10),
    )

    private fun command(
        contentId: String = "3113671",
        language: DomainLocale = DomainLocale.KO,
    ) = SyncFestivalCommand(
        contentId = contentId,
        language = language,
        period = period,
        progressType = "선택안함",
        festivalType = null,
    )

    @Test
    fun `축제 전용 4개 값이 그대로 담긴다`() {
        // AC-1.
        val command = command()

        assertEquals(LocalDate.of(2026, 8, 1), command.period.start)
        assertEquals(LocalDate.of(2026, 8, 10), command.period.end)
        assertEquals("선택안함", command.progressType)
        assertEquals(null, command.festivalType)
    }

    @Test
    fun `contentId가 비면 조립이 거부된다`() {
        // AC-4. FK 위반으로 적재 시점에 터지기 전에 막는다.
        assertFailsWith<IllegalArgumentException> { command(contentId = "") }
        assertFailsWith<IllegalArgumentException> { command(contentId = "   ") }
    }

    @Test
    fun `같은 contentId라도 언어가 다르면 서로 다른 적재 입력이다`() {
        // AC-2. 같은 축제라도 언어별 contentId가 다르지만, 우연히 겹쳐도 언어가 키의 일부다.
        val ko = command(language = DomainLocale.KO)
        val en = command(language = DomainLocale.EN)

        assertNotEquals(ko, en)
        assertEquals(2, setOf(ko, en).size)
    }
}
