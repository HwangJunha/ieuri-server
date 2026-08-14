package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.tourism.domain.aggregate.CatalogTranslation
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.CatalogNameSource
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.TranslationSource
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogTranslationEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewTranslationEntities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 카탈로그 도메인↔엔티티 변환(POL-033).
 *
 * **필드마다 값을 다르게 준다** — 같은 값을 쓰면 뒤바뀐 매핑이 통과한다.
 */
class SpotCatalogMapperTest {

    @Test
    @DisplayName("엔티티 → 도메인 · 필드별로 대조한다")
    fun `entity to domain maps every field`() {
        val entity = SpotCatalogEntity(
            id = 4321,
            representativeNameKo = "마린시티",
            nameSource = CatalogNameSource.DERIVED,
        )
        val translations = listOf(
            SpotCatalogTranslationEntity(
                id = 11,
                catalogId = 4321,
                language = DomainLocale.EN,
                title = "Marine City",
                address = "Haeundae-gu, Busan",
                source = TranslationSource.OFFICIAL,
            ),
            SpotCatalogTranslationEntity(
                id = 12,
                catalogId = 4321,
                language = DomainLocale.JA,
                title = "マリンシティ",
                address = "釜山広域市 海雲台区",
                source = TranslationSource.MACHINE,
            ),
        )

        val domain = entity.toDomain(translations)

        assertEquals(4321L, domain.id)
        assertEquals("마린시티", domain.representativeNameKo)
        assertEquals(CatalogNameSource.DERIVED, domain.nameSource)
        assertEquals(2, domain.translations.size)
        assertEquals("Marine City", domain.translations[DomainLocale.EN]?.title)
        assertEquals("Haeundae-gu, Busan", domain.translations[DomainLocale.EN]?.address)
        assertEquals(TranslationSource.OFFICIAL, domain.translations[DomainLocale.EN]?.source)
        assertEquals("マリンシティ", domain.translations[DomainLocale.JA]?.title)
        assertEquals(TranslationSource.MACHINE, domain.translations[DomainLocale.JA]?.source)
    }

    @Test
    @DisplayName("엔티티 → 도메인 · 번역이 없어도 변환된다")
    fun `entity to domain with no translations`() {
        val entity = SpotCatalogEntity(
            id = 7,
            representativeNameKo = "마린시티",
            nameSource = CatalogNameSource.OFFICIAL,
        )

        val domain = entity.toDomain(emptyList())

        assertEquals(7L, domain.id)
        assertTrue(domain.translations.isEmpty())
    }

    @Test
    @DisplayName("번역 엔티티 → 도메인 값")
    fun `translation entity to domain`() {
        val entity = SpotCatalogTranslationEntity(
            id = 99,
            catalogId = 4321,
            language = DomainLocale.ZH,
            title = "海洋城市",
            address = "釜山廣域市 海雲臺區",
            source = TranslationSource.MACHINE,
        )

        val domain = entity.toDomain()

        assertEquals("海洋城市", domain.title)
        assertEquals("釜山廣域市 海雲臺區", domain.address)
        assertEquals(TranslationSource.MACHINE, domain.source)
    }

    @Test
    @DisplayName("도메인 → 신규 엔티티 · id는 DB가 채운다")
    fun `domain to new entity`() {
        val catalog = SpotCatalog.fromKoreanSpot("마린시티")

        val entity = catalog.toNewEntity()

        assertEquals(null, entity.id)
        assertEquals("마린시티", entity.representativeNameKo)
        assertEquals(CatalogNameSource.OFFICIAL, entity.nameSource)
    }

    @Test
    @DisplayName("도메인 → 신규 번역 엔티티 · 부모 id를 받아 채운다")
    fun `domain to new translation entities`() {
        val catalog = SpotCatalog.restore(
            id = 4321,
            representativeNameKo = "마린시티",
            nameSource = CatalogNameSource.OFFICIAL,
            translations = mapOf(
                DomainLocale.KO to CatalogTranslation("마린시티", "부산광역시 해운대구", TranslationSource.OFFICIAL),
                DomainLocale.JA to CatalogTranslation("マリンシティ", "釜山広域市 海雲台区", TranslationSource.MACHINE),
            ),
        )

        val entities = catalog.toNewTranslationEntities(catalogId = 5555).associateBy { it.language }

        assertEquals(2, entities.size)
        // 부모 id는 인자로 받은 값이지 도메인의 id가 아니다 — 신규 저장 시 둘이 다르다.
        assertTrue(entities.values.all { it.catalogId == 5555L })
        assertEquals("마린시티", entities[DomainLocale.KO]?.title)
        // 주소도 함께 옮겨져야 한다(R-4) — 제목만 흐르면 그 언어 사용자가 찾아갈 수 없다.
        assertEquals("부산광역시 해운대구", entities[DomainLocale.KO]?.address)
        assertEquals(TranslationSource.OFFICIAL, entities[DomainLocale.KO]?.source)
        assertEquals("マリンシティ", entities[DomainLocale.JA]?.title)
        assertEquals("釜山広域市 海雲台区", entities[DomainLocale.JA]?.address)
        assertEquals(TranslationSource.MACHINE, entities[DomainLocale.JA]?.source)
    }
}
