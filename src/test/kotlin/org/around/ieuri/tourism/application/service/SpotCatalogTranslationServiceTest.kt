package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.event.TranslationTargetsFound
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.query.CatalogCandidate
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.port.out.query.UnassignedSpot
import org.around.ieuri.tourism.application.port.out.query.UntranslatedCatalog
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 번역 대상 선택(SPEC-0019 R-4). **이 도메인은 번역하지 않고 대상만 찾아 알린다** —
 * 무엇이 이벤트에 실리는가만 본다.
 */
class SpotCatalogTranslationServiceTest {

    private val targets = setOf(DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)

    private fun untranslated(missing: Set<DomainLocale>) = UntranslatedCatalog(
        catalogId = 900,
        koreanTitle = "마린시티",
        koreanAddress = "부산광역시 해운대구",
        missingLanguages = missing,
    )

    private fun service(query: StubQueryPort, publisher: RecordingPublisher) =
        SpotCatalogService(query, NoOpStorePort, publisher)

    @Test
    @DisplayName("빠진 언어만 실어 보낸다")
    fun `publishes only the missing languages`() {
        val publisher = RecordingPublisher()
        val service = service(
            StubQueryPort(listOf(untranslated(setOf(DomainLocale.JA, DomainLocale.ZH)))),
            publisher,
        )

        val result = service.translateMissing(targets, limit = 10)

        assertEquals(1, result.translated)
        val event = publisher.published.filterIsInstance<TranslationTargetsFound>().single()
        assertEquals(
            setOf(DomainLocale.JA, DomainLocale.ZH),
            event.targets.single().missingLanguages,
            "이미 있는 EN은 실리지 않는다",
        )
    }

    @Test
    @DisplayName("제목과 주소가 함께 실린다")
    fun `title and address travel together`() {
        // 제목만 흐르면 그 언어 사용자가 이름은 읽어도 찾아갈 수 없다(R-4).
        val publisher = RecordingPublisher()
        val service = service(StubQueryPort(listOf(untranslated(setOf(DomainLocale.JA)))), publisher)

        service.translateMissing(targets, limit = 10)

        val target = publisher.published.filterIsInstance<TranslationTargetsFound>().single().targets.single()
        assertEquals(900L, target.catalogId)
        assertEquals("마린시티", target.koreanTitle)
        assertEquals("부산광역시 해운대구", target.koreanAddress)
    }

    @Test
    @DisplayName("요청 언어가 이미 다 있으면 실리지 않는다")
    fun `catalogs with nothing missing are skipped`() {
        val publisher = RecordingPublisher()
        val service = service(StubQueryPort(listOf(untranslated(emptySet()))), publisher)

        val result = service.translateMissing(targets, limit = 10)

        assertEquals(0, result.translated)
        assertEquals(1, result.skipped)
        assertTrue(publisher.published.isEmpty(), "대상이 없으면 발행하지 않는다")
    }

    @Test
    @DisplayName("요청 언어와 겹치지 않는 결손은 대상이 아니다")
    fun `missing languages outside the request are ignored`() {
        val publisher = RecordingPublisher()
        val service = service(StubQueryPort(listOf(untranslated(setOf(DomainLocale.JA)))), publisher)

        val result = service.translateMissing(setOf(DomainLocale.EN), limit = 10)

        assertEquals(0, result.translated)
        assertTrue(publisher.published.isEmpty())
    }

    @Test
    @DisplayName("대상이 하나도 없으면 이벤트를 발행하지 않는다")
    fun `no event when there is nothing to translate`() {
        // 구독자가 빈 알림을 거르지 않아도 되게 한다.
        val publisher = RecordingPublisher()
        val service = service(StubQueryPort(emptyList()), publisher)

        service.translateMissing(targets, limit = 10)

        assertTrue(publisher.published.isEmpty())
    }

    // ── 스텁 ────────────────────────────────────────────────────────────────

    private class StubQueryPort(
        private val untranslated: List<UntranslatedCatalog>,
    ) : SpotCatalogQueryPort {
        override fun findUnassigned(language: DomainLocale, afterSpotId: Long, limit: Int): List<UnassignedSpot> =
            emptyList()

        override fun loadKoreanTitleIndex(): Map<String, List<CatalogCandidate>> = emptyMap()

        override fun loadKoreanThumbnailIndex(): Map<String, Long> = emptyMap()

        override fun findCatalogsMissingTranslation(
            languages: Set<DomainLocale>,
            limit: Int,
        ): List<UntranslatedCatalog> = untranslated
    }

    private class RecordingPublisher : TourismEventPublisherPort {
        val published = mutableListOf<Any>()

        override fun publish(event: Any) {
            published += event
        }
    }

    private object NoOpStorePort : SpotCatalogStorePort {
        override fun save(catalog: SpotCatalog): Long = 1L

        override fun assign(spotId: Long, catalogId: Long) = Unit

        override fun upsertTranslation(
            catalogId: Long,
            language: DomainLocale,
            title: String,
            address: String?,
        ) = Unit

        override fun refreshFestivalPeriod(catalogId: Long) = Unit

        override fun purgeOrphanTranslations(): Int = 0
    }
}
