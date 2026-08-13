package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.around.ieuri.tourism.application.port.out.persistence.SpotActivationCounts
import org.around.ieuri.tourism.application.port.out.persistence.SpotActivationPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotActivationRow
import org.around.ieuri.tourism.domain.specification.SpotExclusionRule
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 판정 결과 적용(SPEC-0018 R8~R12·R19). 포트를 스텁으로 두고 **어느 집합이 제외로 넘어가는가**를
 * 검사한다 — 활성 대상은 그 여집합이라 포트가 SQL로 표현하므로, 서비스가 결정하는 것은 이
 * 집합 하나뿐이다.
 */
class SpotActivationServiceTest {

    private val activationPort = mockk<SpotActivationPort>()
    private val rule = SpotExclusionRule(
        keywords = listOf("약국", "CU"),
        branchNamePatterns = listOf("병원점"),
    )
    private val service = SpotActivationService(activationPort, rule)

    private fun row(
        spotId: Long,
        title: String,
        groupKey: String,
        category: SpotCategory = SpotCategory.SHOPPING,
        language: DomainLocale = DomainLocale.KO,
    ) = SpotActivationRow(spotId, title, category, language, groupKey)

    /** 포트로 넘어간 제외 id 집합을 호출 순서대로 잡아 둔다. */
    private fun captureExcluded(
        counts: SpotActivationCounts = SpotActivationCounts(deactivated = 0, reactivated = 0),
    ): MutableList<List<Long>> {
        val calls = mutableListOf<List<Long>>()
        val ids = slot<Collection<Long>>()
        every { activationPort.applyActivation(capture(ids)) } answers {
            calls += ids.captured.toList()
            counts
        }
        return calls
    }

    // ── R8: 언어 그룹 전파 ──────────────────────────────────────────────────

    @Test
    fun `한 행만 걸린 썸네일 그룹은 전체가 제외 대상이 된다`() {
        // ja·zh 제목이 한글명을 병기하지 않는 경우를 상정한다 — 업스트림 표기는 계약이 아니다.
        every { activationPort.findAllForJudgement() } returns listOf(
            row(1L, "명동명약국", groupKey = "https://cdn/a.jpg", language = DomainLocale.KO),
            row(2L, "Myeongdong Store", groupKey = "https://cdn/a.jpg", language = DomainLocale.EN),
            row(3L, "ミョンドン店", groupKey = "https://cdn/a.jpg", language = DomainLocale.JA),
        )
        val excluded = captureExcluded()

        service.applyExclusions()

        assertEquals(listOf(1L, 2L, 3L), excluded.single())
    }

    @Test
    fun `다른 썸네일 그룹은 전파되지 않는다`() {
        every { activationPort.findAllForJudgement() } returns listOf(
            row(1L, "명동명약국", groupKey = "https://cdn/a.jpg"),
            row(2L, "경복궁", groupKey = "https://cdn/b.jpg"),
        )
        val excluded = captureExcluded()

        service.applyExclusions()

        // 2번은 제외 집합에 없다 — 포트가 여집합을 활성으로 되돌린다.
        assertEquals(listOf(1L), excluded.single())
    }

    // ── R19: 양방향 판정 ────────────────────────────────────────────────────

    @Test
    fun `제외 대상이 없으면 빈 집합을 넘긴다`() {
        // 빈 집합은 "전 행을 활성으로"라는 뜻이다 — 오탐 교정이 설정 변경만으로 이루어지려면
        // 이 방향이 반드시 있어야 한다.
        every { activationPort.findAllForJudgement() } returns listOf(
            row(1L, "경복궁", groupKey = "https://cdn/b.jpg"),
        )
        val excluded = captureExcluded()

        service.applyExclusions()

        assertTrue(excluded.single().isEmpty())
    }

    @Test
    fun `보호 규칙에 걸린 행은 제외 집합에 들어가지 않는다`() {
        every { activationPort.findAllForJudgement() } returns listOf(
            row(1L, "올리브영 제주대학병원점", groupKey = "https://cdn/c.jpg"),
            row(2L, "서울 대한의원", groupKey = "https://cdn/d.jpg", category = SpotCategory.TOURIST_SPOT),
        )
        val excluded = captureExcluded()

        service.applyExclusions()

        assertTrue(excluded.single().isEmpty())
    }

    // ── R10·R19: 건수는 실제로 바뀐 행만 ────────────────────────────────────

    @Test
    fun `전환 건수는 포트가 돌려준 값 그대로다`() {
        // 값이 이미 같은 행은 포트의 `WHERE active <> ?`에 걸러진다 — 서비스는 그 수를 그대로 쓴다.
        every { activationPort.findAllForJudgement() } returns listOf(
            row(1L, "명동명약국", groupKey = "https://cdn/a.jpg"),
            row(2L, "경복궁", groupKey = "https://cdn/b.jpg"),
        )
        captureExcluded(SpotActivationCounts(deactivated = 7, reactivated = 3))

        val result = service.applyExclusions()

        assertEquals(2, result.judged)
        assertEquals(7, result.deactivated)
        assertEquals(3, result.reactivated)
    }

    @Test
    fun `재판정이 전환 건수에 잡히지 않으면 0이다`() {
        // 안정 상태 — 설정과 데이터가 그대로면 두 번째 실행부터 전환이 없다.
        every { activationPort.findAllForJudgement() } returns listOf(
            row(1L, "명동명약국", groupKey = "https://cdn/a.jpg"),
        )
        captureExcluded()

        val result = service.applyExclusions()

        assertEquals(0, result.deactivated)
        assertEquals(0, result.reactivated)
    }

    @Test
    fun `두 번 실행해도 제외 집합이 같다`() {
        // 실행 회차를 입력으로 받지 않으므로 첫 실행과 두 번째 실행이 같아야 한다.
        every { activationPort.findAllForJudgement() } returns listOf(
            row(1L, "명동명약국", groupKey = "https://cdn/a.jpg"),
            row(2L, "경복궁", groupKey = "https://cdn/b.jpg"),
        )
        val excluded = captureExcluded()

        service.applyExclusions()
        service.applyExclusions()

        assertEquals(excluded[0], excluded[1])
    }

    // ── R12·경계 ────────────────────────────────────────────────────────────

    @Test
    fun `키워드가 비면 제외 집합도 빈다`() {
        // 빈 목록을 "전부 제외"로 읽으면 설정 실수 한 번이 전 행을 비활성으로 만든다.
        val emptyRule = SpotExclusionRule(keywords = emptyList(), branchNamePatterns = emptyList())
        val emptyService = SpotActivationService(activationPort, emptyRule)
        every { activationPort.findAllForJudgement() } returns listOf(
            row(1L, "명동명약국", groupKey = "https://cdn/a.jpg"),
            row(2L, "CU 종로점", groupKey = "https://cdn/b.jpg"),
        )
        val excluded = captureExcluded()

        emptyService.applyExclusions()

        assertTrue(excluded.single().isEmpty())
    }

    @Test
    fun `적재된 행이 없으면 빈 집합으로 부르고 건수는 0이다`() {
        every { activationPort.findAllForJudgement() } returns emptyList()
        val excluded = captureExcluded()

        val result = service.applyExclusions()

        assertTrue(excluded.single().isEmpty())
        assertEquals(0, result.judged)
        assertEquals(0, result.deactivated)
        assertEquals(0, result.reactivated)
    }
}
