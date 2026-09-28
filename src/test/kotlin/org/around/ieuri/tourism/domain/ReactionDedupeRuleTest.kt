package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.specification.ReactionDedupeRule
import org.around.ieuri.tourism.domain.specification.ReactionRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/** 좋아요 dedupe 규칙(SPEC-0019 R-10). 결정적이어야 한다 — 재실행해도 같은 행이 남는다. */
class ReactionDedupeRuleTest {

    private val earlier = Instant.parse("2026-08-13T00:00:00Z")
    private val later = Instant.parse("2026-08-14T00:00:00Z")

    private fun row(id: Long, updatedAt: Instant, memberId: Long = 7, catalogId: Long = 900) = ReactionRow(id = id, memberId = memberId, catalogId = catalogId, updatedAt = updatedAt)

    @Test
    @DisplayName("updated_at이 최신인 행을 남긴다")
    fun `latest updatedAt survives`() {
        // 마지막 의사표시가 현재 의사다 — KO에서 LIKE, EN에서 DISLIKE라면 나중 것이 남는다.
        val survivor = ReactionDedupeRule.survivor(
            listOf(row(id = 1, updatedAt = earlier), row(id = 2, updatedAt = later)),
        )

        assertEquals(2L, survivor.id)
    }

    @Test
    @DisplayName("동률이면 id가 큰 행을 남긴다")
    fun `ties are broken by larger id`() {
        val survivor = ReactionDedupeRule.survivor(
            listOf(row(id = 5, updatedAt = later), row(id = 9, updatedAt = later)),
        )

        assertEquals(9L, survivor.id)
    }

    @Test
    @DisplayName("동률 판정은 입력 순서와 무관하게 같다")
    fun `tie break is deterministic regardless of input order`() {
        // 재실행 시 조회 순서가 달라져도 같은 행이 남아야 한다.
        val ascending = listOf(row(id = 5, updatedAt = later), row(id = 9, updatedAt = later))
        val descending = ascending.reversed()

        assertEquals(
            ReactionDedupeRule.survivor(ascending).id,
            ReactionDedupeRule.survivor(descending).id,
        )
    }

    @Test
    @DisplayName("네 언어 모두 좋아요해도 한 행만 남는다")
    fun `four language conflict collapses to one`() {
        // 지원 언어가 4개라 한 카탈로그에 최대 4행이 충돌한다.
        val rows = listOf(
            row(id = 1, updatedAt = earlier),
            row(id = 2, updatedAt = earlier),
            row(id = 3, updatedAt = later),
            row(id = 4, updatedAt = earlier),
        )

        val plan = ReactionDedupeRule.plan(rows)

        assertEquals(1, plan.survivors.size)
        assertEquals(3L, plan.survivors.single().id)
        assertEquals(listOf(1L, 2L, 4L), plan.deletedIds)
    }

    @Test
    @DisplayName("회원과 카탈로그가 다르면 충돌이 아니다")
    fun `different member or catalog does not conflict`() {
        val rows = listOf(
            row(id = 1, updatedAt = later, memberId = 7, catalogId = 900),
            row(id = 2, updatedAt = later, memberId = 8, catalogId = 900),
            row(id = 3, updatedAt = later, memberId = 7, catalogId = 901),
        )

        val plan = ReactionDedupeRule.plan(rows)

        assertEquals(3, plan.survivors.size)
        assertEquals(emptyList<Long>(), plan.deletedIds)
    }

    @Test
    @DisplayName("두 번 실행해도 같은 행이 남는다")
    fun `rerunning yields the same survivor`() {
        val rows = listOf(row(id = 5, updatedAt = later), row(id = 9, updatedAt = later))

        val first = ReactionDedupeRule.plan(rows)
        // 1회차에서 지운 행을 뺀 상태가 2회차의 입력이다.
        val remaining = rows.filterNot { it.id in first.deletedIds }
        val second = ReactionDedupeRule.plan(remaining)

        assertEquals(listOf(9L), second.survivors.map { it.id })
        assertEquals(emptyList<Long>(), second.deletedIds)
    }
}
