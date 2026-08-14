package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.out.client.SpotTitleTranslationPort
import org.around.ieuri.tourism.application.port.out.client.TranslatedCard
import org.around.ieuri.tourism.application.port.out.query.ForeignCard
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.port.out.query.UnassignedSpot
import org.around.ieuri.tourism.application.port.out.query.UnresolvedSpotReport
import org.around.ieuri.tourism.application.port.out.query.UntranslatedCatalog
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.KoreanSpotCandidate
import org.around.ieuri.tourism.domain.vo.TranslationSource
import org.around.ieuri.tourism.domain.vo.UnresolvedReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 번역 대상 선택(SPEC-0019 R-4·R-7). 포트를 스텁으로 두고 **어떤 쌍이 번역되는가**만 본다 —
 * 번역 엔진 자체는 테스트 범위 밖이다.
 */
class SpotCatalogTranslationServiceTest {

    private val targets = setOf(DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)

    @Test
    @DisplayName("빠진 언어만 번역하고 이미 있는 언어는 건드리지 않는다")
    fun `translates only missing languages`() {
        // 카탈로그에 KO와 EN이 있고 JA·ZH가 없는 경우.
        val query = StubQueryPort(
            listOf(
                UntranslatedCatalog(
                    catalogId = 900,
                    representativeNameKo = "마린시티",
                    representativeAddressKo = "부산광역시 해운대구",
                    missingLanguages = setOf(DomainLocale.JA, DomainLocale.ZH),
                ),
            ),
        )
        val store = RecordingStorePort()
        val translation = StubTranslationPort(
            mapOf(DomainLocale.JA to "マリンシティ", DomainLocale.ZH to "海洋城市"),
        )
        val service = SpotCatalogService(query, store, translation)

        val result = service.translateMissing(targets, limit = 10)

        assertEquals(2, result.translated)
        assertEquals(setOf(DomainLocale.JA, DomainLocale.ZH), translation.requested)
        // **주소가 번역 원문으로 전달되어야 한다**(R-4) — 빠지면 번역기가 주소를 만들 수 없다.
        assertEquals("부산광역시 해운대구", translation.requestedAddress)
        // 그리고 번역된 주소가 저장되어야 한다.
        assertEquals(
            listOf("translated-JA", "translated-ZH"),
            store.translations.sortedBy { it.language.name }.map { it.address },
        )
        assertEquals(
            listOf(
                Triple(900L, DomainLocale.JA, TranslationSource.MACHINE),
                Triple(900L, DomainLocale.ZH, TranslationSource.MACHINE),
            ),
            store.translations.map { Triple(it.catalogId, it.language, it.source) }.sortedBy { it.second.name },
        )
    }

    @Test
    @DisplayName("네 언어가 모두 있으면 아무것도 번역하지 않는다")
    fun `translates nothing when all languages exist`() {
        val query = StubQueryPort(emptyList())
        val store = RecordingStorePort()
        val translation = StubTranslationPort(emptyMap())
        val service = SpotCatalogService(query, store, translation)

        val result = service.translateMissing(targets, limit = 10)

        assertEquals(0, result.translated)
        assertTrue(store.translations.isEmpty())
        assertTrue(translation.requested.isEmpty(), "번역 엔진을 호출하지 않아야 한다")
    }

    @Test
    @DisplayName("번역기가 돌려주지 않은 언어는 저장하지 않는다")
    fun `unreturned language is not stored`() {
        // 빈 문자열이나 원문을 넣으면 "번역됨"으로 기록되어 다음 실행이 다시 시도하지 않는다.
        val query = StubQueryPort(
            listOf(
                UntranslatedCatalog(
                    catalogId = 900,
                    representativeNameKo = "마린시티",
                    representativeAddressKo = "부산광역시 해운대구",
                    missingLanguages = setOf(DomainLocale.JA, DomainLocale.ZH),
                ),
            ),
        )
        val store = RecordingStorePort()
        val translation = StubTranslationPort(mapOf(DomainLocale.JA to "マリンシティ"))
        val service = SpotCatalogService(query, store, translation)

        val result = service.translateMissing(targets, limit = 10)

        assertEquals(1, result.translated)
        assertEquals(1, result.skipped)
        assertEquals(listOf(DomainLocale.JA), store.translations.map { it.language })
    }

    @Test
    @DisplayName("요청 언어 밖의 빠진 언어는 번역하지 않는다")
    fun `missing language outside the request is ignored`() {
        val query = StubQueryPort(
            listOf(
                UntranslatedCatalog(
                    catalogId = 900,
                    representativeNameKo = "마린시티",
                    representativeAddressKo = "부산광역시 해운대구",
                    missingLanguages = setOf(DomainLocale.JA, DomainLocale.KO),
                ),
            ),
        )
        val store = RecordingStorePort()
        val translation = StubTranslationPort(mapOf(DomainLocale.JA to "マリンシティ"))
        val service = SpotCatalogService(query, store, translation)

        service.translateMissing(targets, limit = 10)

        // KO는 원문이라 대상이 아니다(R-4: 번역은 KO → EN/JA/ZH 단방향).
        assertEquals(setOf(DomainLocale.JA), translation.requested)
    }

    /** 번역 요청을 기록하고 정해진 결과만 돌려주는 스텁. */
    private class StubTranslationPort(
        private val results: Map<DomainLocale, String>,
    ) : SpotTitleTranslationPort {
        val requested = mutableSetOf<DomainLocale>()
        /** 번역 요청에 실린 KO 주소. 주소가 원문으로 전달되는지 본다(R-4). */
        var requestedAddress: String? = null

        override fun translate(
            koreanTitle: String,
            koreanAddress: String?,
            targets: Set<DomainLocale>,
        ): Map<DomainLocale, TranslatedCard> {
            requested += targets
            requestedAddress = koreanAddress
            return results.filterKeys { it in targets }
                .mapValues { (language, title) -> TranslatedCard(title, "translated-" + language.name) }
        }
    }

    private class StubQueryPort(
        private val untranslated: List<UntranslatedCatalog>,
    ) : SpotCatalogQueryPort {
        override fun findUnassigned(limit: Int): List<UnassignedSpot> = emptyList()

        override fun findForeignCardsByThumbnail(thumbnailImageUrl: String): Map<DomainLocale, ForeignCard> =
            emptyMap()

        override fun findCatalogsMissingTranslation(
            languages: Set<DomainLocale>,
            limit: Int,
        ): List<UntranslatedCatalog> = untranslated

        override fun findUnresolved(): List<UnresolvedSpotReport> = emptyList()
    }

    /** 저장된 번역을 그대로 모아 두는 스텁. */
    private class RecordingStorePort : SpotCatalogStorePort {
        data class Stored(
            val catalogId: Long,
            val language: DomainLocale,
            val title: String,
            val address: String?,
            val source: TranslationSource,
        )

        val translations = mutableListOf<Stored>()

        override fun save(catalog: SpotCatalog): Long = 1

        override fun assign(spotId: Long, catalogId: Long) = Unit

        override fun findKoreanCandidatesByTitleKey(titleKey: String): List<KoreanSpotCandidate> = emptyList()

        override fun findKoreanCandidatesByThumbnail(thumbnailImageUrl: String): List<KoreanSpotCandidate> =
            emptyList()

        override fun recordUnresolved(
            spotId: Long,
            reason: UnresolvedReason,
            extractedKey: String?,
            candidateSpotIds: List<Long>,
        ) = Unit

        override fun upsertTranslation(
            catalogId: Long,
            language: DomainLocale,
            title: String,
            address: String?,
            source: TranslationSource,
        ) {
            translations += Stored(catalogId, language, title, address, source)
        }
    }
}
