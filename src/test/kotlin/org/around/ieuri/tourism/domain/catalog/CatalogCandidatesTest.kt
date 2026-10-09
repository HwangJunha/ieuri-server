package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** 카탈로그 배정 판정(SPEC-0019 R-1·R-2) — 어떤 키로 찾고, 언제 붙이고 언제 새로 만드는가. */
class CatalogCandidatesTest {

    private fun candidate(id: Long, mapX: Double = MAP_X, mapY: Double = MAP_Y, thumb: String = THUMB) = CatalogCandidate(id, mapX, mapY, thumb)

    private fun joinedTo(assignment: CatalogAssignment): Long = (assignment as CatalogAssignment.Join).catalogId

    private fun founded(assignment: CatalogAssignment): SpotCatalog = (assignment as CatalogAssignment.Found).catalog

    @Test
    @DisplayName("비한국어는 괄호에서 뽑은 한국어로 KO 카탈로그를 찾는다")
    fun `foreign origin looks up by the extracted korean key`() {
        val index = CatalogCandidates(mapOf("마린시티" to listOf(candidate(900))))

        assertEquals(900L, joinedTo(index.assign(origin())))
    }

    @Test
    @DisplayName("비한국어는 후보가 하나면 좌표를 묻지 않는다")
    fun `foreign origin with a single candidate ignores coordinates`() {
        val index = CatalogCandidates(mapOf("마린시티" to listOf(candidate(900, mapX = 127.0, mapY = 37.5))))

        assertEquals(900L, joinedTo(index.assign(origin())))
    }

    @Test
    @DisplayName("제목이 달라도 썸네일이 같으면 KO에 붙는다")
    fun `thumbnail fallback links origins whose titles differ`() {
        val index = CatalogCandidates(emptyMap(), mapOf(THUMB to 900L))

        assertEquals(900L, joinedTo(index.assign(origin(title = "韓華渡假村/龍仁(한화리조트 / 용인)"))))
    }

    @Test
    @DisplayName("제목으로 찾으면 썸네일을 보지 않는다")
    fun `title match wins over thumbnail`() {
        val index = CatalogCandidates(mapOf("마린시티" to listOf(candidate(900))), mapOf(THUMB to 777L))

        assertEquals(900L, joinedTo(index.assign(origin())))
    }

    @Test
    @DisplayName("KO 짝이 없으면 자기 언어만 가진 카탈로그를 만든다")
    fun `foreign origin without a korean counterpart founds its own catalog`() {
        val catalog = founded(CatalogCandidates(emptyMap()).assign(origin(title = "SM Entertainment")))

        assertEquals(listOf(DomainLocale.EN), catalog.translations.map { it.language })
        assertEquals("SM Entertainment", catalog.translations.single().title)
    }

    @Test
    @DisplayName("병기된 한국어 괄호는 표시 제목에서 떼어낸다")
    fun `korean parenthetical is stripped from the display title`() {
        val catalog = founded(CatalogCandidates(emptyMap()).assign(origin(title = "Busan Tower (부산타워)")))

        assertEquals("Busan Tower", catalog.translations.single().title)
    }

    @Test
    @DisplayName("KO 제목은 그대로 쓴다")
    fun `korean title is kept as is`() {
        val catalog = founded(CatalogCandidates(emptyMap()).assign(koreanOrigin(title = "부산타워 (전망대)")))

        assertEquals("부산타워 (전망대)", catalog.translations.single().title)
    }

    @Test
    @DisplayName("표시할 제목이 없는 비한국어 원본은 배정하지 않는다")
    fun `foreign origin with only korean in its title is skipped`() {
        assertSame(CatalogAssignment.Skip, CatalogCandidates(mapOf("마린시티" to listOf(candidate(900)))).assign(origin(title = "마린시티")))
    }

    @Test
    @DisplayName("KO는 같은 키가 여럿이면 좌표·썸네일이 모두 같은 것에 붙인다")
    fun `korean origin picks the candidate with the same coordinates and thumbnail`() {
        val index = CatalogCandidates(mapOf("마린시티" to listOf(candidate(900, mapX = 127.0), candidate(901))))

        assertEquals(901L, joinedTo(index.assign(koreanOrigin())))
    }

    @Test
    @DisplayName("KO는 좌표가 같아도 썸네일이 다르면 다른 장소다")
    fun `korean origin with a different thumbnail is a different place`() {
        val index = CatalogCandidates(mapOf("마린시티" to listOf(candidate(900, thumb = "https://img/other.jpg"))))

        founded(index.assign(koreanOrigin()))
    }

    @Test
    @DisplayName("좌표·썸네일로도 가릴 수 없으면 새로 만든다")
    fun `ambiguous candidates found a new catalog`() {
        val index = CatalogCandidates(mapOf("마린시티" to listOf(candidate(900), candidate(901))))

        founded(index.assign(koreanOrigin()))
    }

    @Test
    @DisplayName("같은 회차에서 만든 KO 카탈로그에 다음 KO 원본이 붙는다")
    fun `korean origins in one round fold into the catalog founded earlier`() {
        val first = koreanOrigin(spotId = 12)
        val index = CatalogCandidates(emptyMap()).withFounded(first, catalogId = 500)

        assertEquals(500L, joinedTo(index.assign(koreanOrigin(spotId = 13))))
    }

    @Test
    @DisplayName("같은 회차의 KO끼리도 좌표가 다르면 접지 않는다")
    fun `korean origins with different coordinates do not fold`() {
        val index = CatalogCandidates(emptyMap()).withFounded(koreanOrigin(spotId = 12), catalogId = 500)

        founded(index.assign(koreanOrigin(spotId = 13, mapX = 128.0)))
    }

    @Test
    @DisplayName("비한국어로 만든 카탈로그는 후보에 더하지 않는다")
    fun `foreign founded catalogs are not candidates`() {
        val index = CatalogCandidates(emptyMap()).withFounded(origin(spotId = 12), catalogId = 500)

        founded(index.assign(origin(spotId = 13)))
    }
}
