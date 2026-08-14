package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.tourism.domain.aggregate.CatalogFestivalPeriod
import org.around.ieuri.tourism.domain.aggregate.CatalogStandardValues
import org.around.ieuri.tourism.domain.aggregate.CatalogTranslation
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.CatalogNameSource
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.TranslationSource
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogFestivalEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogTranslationEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewTranslationEntities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 카탈로그 도메인↔엔티티 변환(POL-033).
 *
 * **필드마다 값을 다르게 준다** — 같은 값을 쓰면 뒤바뀐 매핑이 통과한다.
 */
class SpotCatalogMapperTest {

    @Test
    @DisplayName("엔티티 → 도메인 · 필드별로 대조한다")
    fun `entity to domain maps every field`() {
        // mapX/mapY는 둘 다 Double이라 뒤바뀌어도 컴파일된다 — 값을 확실히 다르게 둔다.
        val entity = SpotCatalogEntity(
            id = 4321,
            representativeNameKo = "마린시티",
            nameSource = CatalogNameSource.DERIVED,
            nameKey = "마린시티",
            mapX = 129.1234567,
            mapY = 35.7654321,
            thumbnailImageUrl = "https://img/marine.jpg",
            category = SpotCategory.TOURIST_SPOT,
            copyright = CopyrightType.TYPE1,
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

        val festival = SpotCatalogFestivalEntity(
            catalogId = 4321,
            eventStartDate = LocalDate.of(2026, 5, 1),
            eventEndDate = LocalDate.of(2026, 5, 9),
        )

        val domain = entity.toDomain(translations, festival)

        assertEquals(4321L, domain.id)
        assertEquals("마린시티", domain.representativeNameKo)
        assertEquals(CatalogNameSource.DERIVED, domain.nameSource)
        assertEquals(129.1234567, domain.standard.mapX)
        assertEquals(35.7654321, domain.standard.mapY)
        assertEquals("https://img/marine.jpg", domain.standard.thumbnailImageUrl)
        assertEquals(SpotCategory.TOURIST_SPOT, domain.standard.category)
        assertEquals(CopyrightType.TYPE1, domain.standard.copyright)
        // 시작일과 종료일도 같은 타입이라 뒤바뀔 수 있다.
        assertEquals(LocalDate.of(2026, 5, 1), domain.festivalPeriod?.startDate)
        assertEquals(LocalDate.of(2026, 5, 9), domain.festivalPeriod?.endDate)
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
        // 표준값이 전부 결손인 카탈로그가 실재한다 — 좌표가 (0,0)·한국 밖인 원본 317건.
        assertNull(domain.standard.mapX)
        assertNull(domain.standard.mapY)
        assertNull(domain.standard.thumbnailImageUrl)
        assertNull(domain.standard.category)
        assertNull(domain.standard.copyright)
        assertNull(domain.festivalPeriod)
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
        val catalog = SpotCatalog.fromKoreanSpot(
            koreanTitle = "마린시티",
            koreanAddress = "부산광역시 해운대구",
            standard = CatalogStandardValues(
                mapX = 129.1234567,
                mapY = 35.7654321,
                thumbnailImageUrl = "https://img/marine.jpg",
                category = SpotCategory.RESTAURANT,
                copyright = CopyrightType.TYPE3,
            ),
        )

        val entity = catalog.toNewEntity()

        assertEquals(null, entity.id)
        assertEquals("마린시티", entity.representativeNameKo)
        assertEquals(CatalogNameSource.OFFICIAL, entity.nameSource)
        // nameKey는 컬럼이 아니라 대표명에서 파생된다 — 저장은 인덱스를 위해서다.
        assertEquals("마린시티", entity.nameKey)
        assertEquals(129.1234567, entity.mapX)
        assertEquals(35.7654321, entity.mapY)
        assertEquals("https://img/marine.jpg", entity.thumbnailImageUrl)
        assertEquals(SpotCategory.RESTAURANT, entity.category)
        assertEquals(CopyrightType.TYPE3, entity.copyright)
    }

    @Test
    @DisplayName("축제 기간 → 신규 엔티티 · 부모 id를 받아 채운다")
    fun `festival period to new entity`() {
        val entity = CatalogFestivalPeriod(
            startDate = LocalDate.of(2026, 5, 1),
            endDate = LocalDate.of(2026, 5, 9),
        ).toNewEntity(catalogId = 5555)

        assertEquals(5555L, entity.catalogId)
        assertEquals(LocalDate.of(2026, 5, 1), entity.eventStartDate)
        assertEquals(LocalDate.of(2026, 5, 9), entity.eventEndDate)
    }

    @Test
    @DisplayName("도메인 → 신규 번역 엔티티 · 부모 id를 받아 채운다")
    fun `domain to new translation entities`() {
        val catalog = SpotCatalog.restore(
            id = 4321,
            representativeNameKo = "마린시티",
            nameSource = CatalogNameSource.OFFICIAL,
            standard = CatalogStandardValues(),
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
