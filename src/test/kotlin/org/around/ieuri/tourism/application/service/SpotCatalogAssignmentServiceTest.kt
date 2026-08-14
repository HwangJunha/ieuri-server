package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.out.client.SpotTitleTranslationPort
import org.around.ieuri.tourism.application.port.out.client.TranslatedCard
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.port.out.query.UnassignedSpot
import org.around.ieuri.tourism.application.port.out.query.UnresolvedSpotReport
import org.around.ieuri.tourism.application.port.out.query.UntranslatedCatalog
import org.around.ieuri.tourism.domain.aggregate.CatalogTranslation
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.CatalogCandidate
import org.around.ieuri.tourism.domain.vo.TranslationSource
import org.around.ieuri.tourism.domain.vo.UnresolvedReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 카탈로그 배정(SPEC-0019 R-1·R-2).
 *
 * **이 테스트가 존재하는 이유는 조회 키를 규칙 단위 테스트가 볼 수 없기 때문이다.**
 * `CatalogMatchRuleTest`는 후보 목록을 직접 넘겨받으므로, 서비스가 **어떤 키로** 후보를
 * 찾았는지는 검사하지 못한다. 제목 전체 키(`MarineCity(마린시티)`)로 찾으면 KO의
 * `마린시티`와 결코 일치하지 않아 매칭이 전량 실패하는데, 규칙 테스트는 전부 통과한다.
 */
class SpotCatalogAssignmentServiceTest {

    private val mapX = 129.1234567
    private val mapY = 35.7654321

    private fun foreignSpot(
        spotId: Long = 21,
        language: DomainLocale = DomainLocale.EN,
        title: String = "Marine City (마린시티)",
        address: String? = "Busan Haeundae-gu",
        mapX: Double = this.mapX,
        mapY: Double = this.mapY,
    ) = UnassignedSpot(
        spotId = spotId,
        language = language,
        title = title,
        address = address,
        thumbnailImageUrl = "https://img/1.jpg",
        mapX = mapX,
        mapY = mapY,
    )

    private fun koreanSpot(
        spotId: Long = 11,
        title: String = "마린시티",
        address: String? = "부산광역시 해운대구",
        mapX: Double = this.mapX,
        mapY: Double = this.mapY,
    ) = UnassignedSpot(
        spotId = spotId,
        language = DomainLocale.KO,
        title = title,
        address = address,
        thumbnailImageUrl = "https://img/1.jpg",
        mapX = mapX,
        mapY = mapY,
    )

    @Test
    @DisplayName("비한국어 행은 괄호에서 뽑은 한국어로 KO 후보를 찾는다")
    fun `looks up candidates by the extracted korean key`() {
        val store = RecordingStorePort(
            candidatesByKey = mapOf(
                "마린시티" to listOf(CatalogCandidate(catalogId = 900, mapX = mapX, mapY = mapY)),
            ),
        )
        val service = SpotCatalogService(StubQueryPort(listOf(foreignSpot())), store, NoTranslation)

        val result = service.assignCatalogs()

        // 제목 전체 키로 찾았다면 후보가 비어 미해결로 떨어졌을 것이다.
        assertEquals(listOf("마린시티"), store.requestedKeys)
        assertEquals(listOf(21L to 900L), store.assignments)
        assertEquals(0, result.unresolved)
        assertEquals(0, result.catalogsCreated)
    }

    @Test
    @DisplayName("전각 괄호 제목도 같은 키로 찾는다")
    fun `fullwidth parens produce the same lookup key`() {
        val store = RecordingStorePort(
            candidatesByKey = mapOf(
                "마린시티" to listOf(CatalogCandidate(catalogId = 900, mapX = mapX, mapY = mapY)),
            ),
        )
        val spot = foreignSpot(language = DomainLocale.JA, title = "マリンシティ（마린시티）")
        val service = SpotCatalogService(StubQueryPort(listOf(spot)), store, NoTranslation)

        service.assignCatalogs()

        assertEquals(listOf("마린시티"), store.requestedKeys)
        assertEquals(listOf(21L to 900L), store.assignments)
    }

    @Test
    @DisplayName("합류한 언어의 제목은 OFFICIAL로 심는다")
    fun `joined language title is stored as official`() {
        val store = RecordingStorePort(
            candidatesByKey = mapOf(
                "마린시티" to listOf(CatalogCandidate(catalogId = 900, mapX = mapX, mapY = mapY)),
            ),
        )
        val service = SpotCatalogService(StubQueryPort(listOf(foreignSpot())), store, NoTranslation)

        service.assignCatalogs()

        // 나중에 번역 대상이 되지 않으려면 지금 심어야 한다(R-4).
        assertEquals(
            listOf(Triple(900L, DomainLocale.EN, TranslationSource.OFFICIAL)),
            store.translations,
        )
        // **주소도 함께 심어야 한다**(R-4) — 빠지면 그 언어 사용자가 장소를 찾아갈 수 없다.
        assertEquals(listOf("Busan Haeundae-gu"), store.addresses)
    }

    @Test
    @DisplayName("한국어를 못 뽑으면 썸네일로 판정한다")
    fun `falls back to thumbnail when no korean key`() {
        val store = RecordingStorePort(
            candidatesByThumbnail = listOf(
                CatalogCandidate(catalogId = 950, mapX = mapX, mapY = mapY),
            ),
        )
        val spot = foreignSpot(title = "SM Entertainment")
        val service = SpotCatalogService(StubQueryPort(listOf(spot)), store, NoTranslation)

        service.assignCatalogs()

        // 키가 없으므로 키 조회 자체를 하지 않는다.
        assertTrue(store.requestedKeys.isEmpty())
        assertEquals(listOf(21L to 950L), store.assignments)
    }

    @Test
    @DisplayName("KO 행은 제목 키와 좌표가 모두 같으면 기존 카탈로그에 접힌다")
    fun `korean rows fold on identical key and coordinates`() {
        // spec R-2의 KO 자체 클러스터링. 51,992행 → 51,907 클러스터(자체 중복 85행).
        val store = RecordingStorePort(
            candidatesByKey = mapOf(
                "마린시티" to listOf(CatalogCandidate(catalogId = 900, mapX = mapX, mapY = mapY)),
            ),
        )
        val service = SpotCatalogService(StubQueryPort(listOf(koreanSpot(spotId = 12))), store, NoTranslation)

        val result = service.assignCatalogs()

        assertEquals(listOf(12L to 900L), store.assignments)
        assertEquals(0, result.catalogsCreated, "기존 카탈로그에 접혔으므로 새로 만들지 않는다")
    }

    @Test
    @DisplayName("KO 행은 좌표가 다르면 접히지 않는다")
    fun `korean rows do not fold when coordinates differ`() {
        // 같은 제목이 전국에 흔하다 — `만두집`. 좌표까지 같아야 같은 장소다.
        val store = RecordingStorePort(
            candidatesByKey = mapOf(
                "마린시티" to listOf(CatalogCandidate(catalogId = 900, mapX = 127.0, mapY = 37.0)),
            ),
        )
        val service = SpotCatalogService(StubQueryPort(listOf(koreanSpot(spotId = 12))), store, NoTranslation)

        val result = service.assignCatalogs()

        assertEquals(1, result.catalogsCreated)
        assertEquals(listOf(12L to RecordingStorePort.NEW_CATALOG_ID), store.assignments)
    }

    @Test
    @DisplayName("매칭 실패한 비한국어 행도 카탈로그를 갖고 미해결로 기록된다")
    fun `unmatched foreign row still gets a catalog`() {
        // R-1: 매칭은 여러 행을 접는 작업이지 자격 심사가 아니다.
        val store = RecordingStorePort()
        val spot = foreignSpot(title = "Phil hotel")
        val service = SpotCatalogService(StubQueryPort(listOf(spot)), store, NoTranslation)

        val result = service.assignCatalogs()

        assertEquals(1, result.unresolved)
        assertEquals(listOf(21L to RecordingStorePort.NEW_CATALOG_ID), store.assignments)
        assertEquals(listOf(21L to UnresolvedReason.NO_KOREAN_KEY), store.unresolved)
    }

    private object NoTranslation : SpotTitleTranslationPort {
        override fun translate(
            koreanTitle: String,
            koreanAddress: String?,
            targets: Set<DomainLocale>,
        ): Map<DomainLocale, TranslatedCard> = emptyMap()
    }

    private class StubQueryPort(
        private val unassigned: List<UnassignedSpot>,
    ) : SpotCatalogQueryPort {
        override fun findUnassigned(limit: Int): List<UnassignedSpot> = unassigned

        override fun findForeignCardsByThumbnail(
            thumbnailImageUrl: String,
        ): Map<DomainLocale, CatalogTranslation> = emptyMap()

        override fun findCatalogsMissingTranslation(
            languages: Set<DomainLocale>,
            limit: Int,
        ): List<UntranslatedCatalog> = emptyList()

        override fun findUnresolved(): List<UnresolvedSpotReport> = emptyList()
    }

    /** 어떤 키로 후보를 찾았는지까지 기록하는 스텁 — 그것이 이 테스트의 핵심 관측 대상이다. */
    private class RecordingStorePort(
        private val candidatesByKey: Map<String, List<CatalogCandidate>> = emptyMap(),
        private val candidatesByThumbnail: List<CatalogCandidate> = emptyList(),
    ) : SpotCatalogStorePort {
        val requestedKeys = mutableListOf<String>()
        val assignments = mutableListOf<Pair<Long, Long>>()
        val translations = mutableListOf<Triple<Long, DomainLocale, TranslationSource>>()
        /** 저장된 주소. 제목만 흐르고 주소가 빠지는 회귀를 잡는다(R-4). */
        val addresses = mutableListOf<String?>()
        val unresolved = mutableListOf<Pair<Long, UnresolvedReason>>()
        /** 표준값을 다시 확정한 카탈로그. 배정할 때마다 불려야 한다(R-1). */
        val refreshedCatalogIds = mutableListOf<Long>()

        override fun save(catalog: SpotCatalog): Long = NEW_CATALOG_ID

        override fun assign(spotId: Long, catalogId: Long) {
            assignments += spotId to catalogId
        }

        override fun findCatalogCandidatesByNameKey(nameKey: String): List<CatalogCandidate> {
            requestedKeys += nameKey
            return candidatesByKey[nameKey].orEmpty()
        }

        override fun findCatalogCandidatesByThumbnail(thumbnailImageUrl: String): List<CatalogCandidate> =
            candidatesByThumbnail

        override fun refreshStandardValues(catalogId: Long) {
            refreshedCatalogIds += catalogId
        }

        override fun recordUnresolved(
            spotId: Long,
            reason: UnresolvedReason,
            extractedKey: String?,
            candidateCatalogIds: List<Long>,
        ) {
            unresolved += spotId to reason
        }

        override fun upsertTranslation(
            catalogId: Long,
            language: DomainLocale,
            title: String,
            address: String?,
            source: TranslationSource,
        ) {
            translations += Triple(catalogId, language, source)
            addresses += address
        }

        companion object {
            const val NEW_CATALOG_ID = 7777L
        }
    }
}
