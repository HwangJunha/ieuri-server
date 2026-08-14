package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.specification.CatalogMatchRule
import org.around.ieuri.tourism.domain.specification.SpotPoint
import org.around.ieuri.tourism.domain.vo.CatalogMatch
import org.around.ieuri.tourism.domain.vo.CatalogCandidate
import org.around.ieuri.tourism.domain.vo.UnresolvedReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 동일성 판정의 우선순위 전체(SPEC-0019 R-2).
 *
 * 좌표는 **완전일치만** 인정한다 — 근사 허용은 오판정을 만든다.
 */
class CatalogMatchRuleTest {

    private val point = SpotPoint(mapX = 129.1234567, mapY = 35.7654321)

    private fun candidate(catalogId: Long, mapX: Double = point.mapX, mapY: Double = point.mapY) =
        CatalogCandidate(catalogId = catalogId, mapX = mapX, mapY = mapY)

    @Test
    @DisplayName("순위 1 · 괄호 키 후보가 유일하면 채택한다")
    fun `single key candidate is adopted`() {
        val match = CatalogMatchRule.match(
            title = "Marine City (마린시티)",
            coordinates = point,
            candidatesByKey = listOf(candidate(catalogId = 900)),
            candidatesByThumbnail = emptyList(),
        )

        assertEquals(CatalogMatch.Matched(900), match)
    }

    @Test
    @DisplayName("전각 괄호도 반각과 동일하게 판정된다")
    fun `fullwidth parens match identically`() {
        val match = CatalogMatchRule.match(
            title = "マリンシティ（마린시티）",
            coordinates = point,
            candidatesByKey = listOf(candidate(catalogId = 900)),
            candidatesByThumbnail = emptyList(),
        )

        assertEquals(CatalogMatch.Matched(900), match)
    }

    @Test
    @DisplayName("순위 2 · 후보가 여럿이면 좌표가 완전일치하는 하나를 채택한다")
    fun `exact coordinate match breaks the tie`() {
        val match = CatalogMatchRule.match(
            title = "Marine City (마린시티)",
            coordinates = point,
            candidatesByKey = listOf(
                candidate(catalogId = 900),
                candidate(catalogId = 901, mapX = 127.0, mapY = 37.0),
            ),
            candidatesByThumbnail = emptyList(),
        )

        assertEquals(CatalogMatch.Matched(900), match)
    }

    @Test
    @DisplayName("좌표가 근소하게 달라도 일치로 보지 않는다")
    fun `near miss coordinates are not a match`() {
        // 마지막 자릿수만 다르다. 근사 허용이 없으므로 미해결이어야 한다.
        val match = CatalogMatchRule.match(
            title = "Marine City (마린시티)",
            coordinates = point,
            candidatesByKey = listOf(
                candidate(catalogId = 900, mapX = 129.1234568, mapY = point.mapY),
                candidate(catalogId = 901, mapX = 127.0, mapY = 37.0),
            ),
            candidatesByThumbnail = emptyList(),
        )

        val unresolved = assertUnresolved(match)
        assertEquals(UnresolvedReason.KEY_AMBIGUOUS, unresolved.reason)
    }

    @Test
    @DisplayName("좌표 완전일치 후보가 둘이면 미해결이다")
    fun `two exact coordinate matches stay unresolved`() {
        val match = CatalogMatchRule.match(
            title = "Marine City (마린시티)",
            coordinates = point,
            candidatesByKey = listOf(
                candidate(catalogId = 900),
                candidate(catalogId = 901),
            ),
            candidatesByThumbnail = emptyList(),
        )

        val unresolved = assertUnresolved(match)
        assertEquals(UnresolvedReason.KEY_AMBIGUOUS, unresolved.reason)
        assertEquals(listOf(900L, 901L), unresolved.candidateCatalogIds)
        assertEquals("마린시티", unresolved.extractedKey)
    }

    @Test
    @DisplayName("순위 3 · 키가 없으면 썸네일 URL로 판정한다")
    fun `thumbnail decides when no korean key exists`() {
        val match = CatalogMatchRule.match(
            title = "SM Entertainment",
            coordinates = point,
            candidatesByKey = emptyList(),
            candidatesByThumbnail = listOf(candidate(catalogId = 950)),
        )

        assertEquals(CatalogMatch.Matched(950), match)
    }

    @Test
    @DisplayName("괄호키와 썸네일이 충돌하면 괄호키를 채택한다")
    fun `bracket key wins over thumbnail on conflict`() {
        // 실측 충돌 201건에서 키 측 카테고리 일치율 97%, URL 측 26%였다.
        val match = CatalogMatchRule.match(
            title = "Marine City (마린시티)",
            coordinates = point,
            candidatesByKey = listOf(candidate(catalogId = 900)),
            candidatesByThumbnail = listOf(candidate(catalogId = 111)),
        )

        assertEquals(CatalogMatch.Matched(900), match)
    }

    @Test
    @DisplayName("키가 있으면 후보가 없어도 썸네일로 내려가지 않는다")
    fun `key presence blocks thumbnail fallback`() {
        val match = CatalogMatchRule.match(
            title = "Marine City (마린시티)",
            coordinates = point,
            candidatesByKey = emptyList(),
            candidatesByThumbnail = listOf(candidate(catalogId = 111)),
        )

        // 썸네일로 떨어졌다면 Matched(111)이 됐을 것이다.
        val unresolved = assertUnresolved(match)
        assertEquals(UnresolvedReason.KEY_AMBIGUOUS, unresolved.reason)
    }

    @Test
    @DisplayName("한국어 키도 썸네일 후보도 없으면 NO_KOREAN_KEY다")
    fun `no key and no thumbnail candidate is NO_KOREAN_KEY`() {
        val match = CatalogMatchRule.match(
            title = "Phil hotel",
            coordinates = point,
            candidatesByKey = emptyList(),
            candidatesByThumbnail = emptyList(),
        )

        val unresolved = assertUnresolved(match)
        assertEquals(UnresolvedReason.NO_KOREAN_KEY, unresolved.reason)
        assertEquals(null, unresolved.extractedKey)
    }

    @Test
    @DisplayName("썸네일을 여러 KO가 공유하면 URL_AMBIGUOUS다")
    fun `shared thumbnail is URL_AMBIGUOUS`() {
        val match = CatalogMatchRule.match(
            title = "SM Entertainment",
            coordinates = point,
            candidatesByKey = emptyList(),
            candidatesByThumbnail = listOf(
                candidate(catalogId = 950),
                candidate(catalogId = 951),
            ),
        )

        val unresolved = assertUnresolved(match)
        assertEquals(UnresolvedReason.URL_AMBIGUOUS, unresolved.reason)
        assertEquals(listOf(950L, 951L), unresolved.candidateCatalogIds)
    }

    private fun assertUnresolved(match: CatalogMatch): CatalogMatch.Unresolved {
        assertTrue(match is CatalogMatch.Unresolved, "미해결이어야 한다: $match")
        return match as CatalogMatch.Unresolved
    }
}
