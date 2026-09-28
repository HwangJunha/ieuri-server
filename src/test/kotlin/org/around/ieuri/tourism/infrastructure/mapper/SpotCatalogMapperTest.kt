package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.tourism.domain.aggregate.CatalogFestivalPeriod
import org.around.ieuri.tourism.domain.aggregate.CatalogStandardValues
import org.around.ieuri.tourism.domain.aggregate.CatalogTranslation
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogFestivalEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogTranslationEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewTranslationEntities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** 카탈로그 매퍼 필드별 매핑 검증. `mapX`·`mapY`처럼 같은 타입 필드가 뒤바뀌지 않도록 필드마다 다른 값을 쓴다. */
class SpotCatalogMapperTest {

    private fun entity(id: Long? = 4321L) = SpotCatalogEntity(
        id = id,
        mapX = 129.1234,
        mapY = 35.5678,
        thumbnailImageUrl = "https://img/1.jpg",
        category = "CULTURAL_FACILITY",
        copyright = CopyrightType.TYPE1,
    )

    private fun standard() = CatalogStandardValues(
        mapX = 129.1234,
        mapY = 35.5678,
        thumbnailImageUrl = "https://img/1.jpg",
        category = SpotCategory("CULTURAL_FACILITY"),
        copyright = CopyrightType.TYPE1,
    )

    @Test
    fun `엔티티를 도메인으로 옮긴다`() {
        val domain = entity().toDomain()

        assertEquals(4321L, domain.id)
        assertEquals(129.1234, domain.standard.mapX)
        assertEquals(35.5678, domain.standard.mapY)
        assertEquals("https://img/1.jpg", domain.standard.thumbnailImageUrl)
        assertEquals(SpotCategory("CULTURAL_FACILITY"), domain.standard.category)
        assertEquals(CopyrightType.TYPE1, domain.standard.copyright)
    }

    @Test
    fun `번역과 축제를 함께 받아 도메인으로 옮긴다`() {
        val domain = entity().toDomain(
            translations = mapOf(DomainLocale.KO to CatalogTranslation("마린시티", "부산광역시 해운대구")),
            festival = SpotCatalogFestivalEntity(
                catalogId = 4321L,
                eventStartDate = LocalDate.of(2026, 10, 1),
                eventEndDate = LocalDate.of(2026, 10, 3),
            ),
        )

        assertEquals("마린시티", domain.translations[DomainLocale.KO]?.title)
        assertEquals(LocalDate.of(2026, 10, 1), domain.festivalPeriod?.startDate)
        assertEquals(LocalDate.of(2026, 10, 3), domain.festivalPeriod?.endDate)
    }

    @Test
    fun `번역과 축제를 주지 않으면 비어 있다`() {
        val domain = entity().toDomain()

        assertEquals(emptyMap<DomainLocale, CatalogTranslation>(), domain.translations)
        assertNull(domain.festivalPeriod)
    }

    @Test
    fun `번역 엔티티를 도메인 값으로 옮긴다`() {
        val translation = SpotCatalogTranslationEntity(
            catalogId = 4321L,
            language = DomainLocale.JA,
            title = "マリンシティ",
            address = "釜山広域市 海雲台区",
            mapX = 129.1234,
            mapY = 35.5678,
            category = "CULTURAL_FACILITY",
        ).toDomain()

        assertEquals("マリンシティ", translation.title)
        assertEquals("釜山広域市 海雲台区", translation.address)
    }

    @Test
    fun `주소가 없는 번역도 옮긴다`() {
        // 원문에 주소가 없는 spot이 있다.
        val translation = SpotCatalogTranslationEntity(
            catalogId = 4321L,
            language = DomainLocale.KO,
            title = "마린시티",
            address = null,
            mapX = 129.1234,
            mapY = 35.5678,
            category = "CULTURAL_FACILITY",
        ).toDomain()

        assertEquals("마린시티", translation.title)
        assertNull(translation.address)
    }

    @Test
    fun `도메인을 새 엔티티로 옮긴다`() {
        val entity = SpotCatalog.from(
            language = DomainLocale.KO,
            title = "마린시티",
            address = "부산광역시 해운대구",
            standard = standard(),
        ).toNewEntity()

        assertNull(entity.id) // id는 DB가 채운다.
        assertEquals(129.1234, entity.mapX)
        assertEquals(35.5678, entity.mapY)
        assertEquals("https://img/1.jpg", entity.thumbnailImageUrl)
        assertEquals("CULTURAL_FACILITY", entity.category)
        assertEquals(CopyrightType.TYPE1, entity.copyright)
    }

    @Test
    fun `도메인 번역들을 새 엔티티 목록으로 옮긴다`() {
        val catalog = SpotCatalog.restore(
            id = 4321,
            standard = standard(),
            translations = mapOf(
                DomainLocale.KO to CatalogTranslation("마린시티", "부산광역시 해운대구"),
                DomainLocale.JA to CatalogTranslation("マリンシティ", "釜山広域市 海雲台区"),
            ),
        )

        val entities = catalog.toNewTranslationEntities(catalogId = 5555).associateBy { it.language }

        assertEquals(2, entities.size)
        // 부모 id는 인자로 받은 값이지 도메인의 id가 아니다.
        assertEquals(5555L, entities[DomainLocale.KO]?.catalogId)
        assertEquals("마린시티", entities[DomainLocale.KO]?.title)
        // 주소도 함께 옮겨져야 한다(R-4).
        assertEquals("부산광역시 해운대구", entities[DomainLocale.KO]?.address)
        assertEquals("マリンシティ", entities[DomainLocale.JA]?.title)
        assertEquals("釜山広域市 海雲台区", entities[DomainLocale.JA]?.address)
    }

    @Test
    fun `번역 엔티티는 카탈로그의 분류를 사본으로 갖는다`() {
        // 검색이 이 사본으로 카테고리를 거른다.
        val catalog = SpotCatalog.restore(
            id = 4321,
            standard = standard(),
            translations = mapOf(DomainLocale.KO to CatalogTranslation("마린시티", null)),
        )

        val entity = catalog.toNewTranslationEntities(catalogId = 5555).single()

        // copyright와 뒤바뀌어도 컴파일되므로 값으로 확인한다.
        assertEquals("CULTURAL_FACILITY", entity.category)
        assertEquals(SpotCategory("CULTURAL_FACILITY"), catalog.standard.category)
    }

    @Test
    fun `번역 엔티티는 카탈로그의 좌표를 사본으로 갖는다`() {
        // 검색은 이 좌표 사본만 본다.
        val catalog = SpotCatalog.restore(
            id = 4321,
            standard = standard(),
            translations = mapOf(DomainLocale.KO to CatalogTranslation("마린시티", null)),
        )

        val entity = catalog.toNewTranslationEntities(catalogId = 5555).single()

        assertEquals(129.1234, entity.mapX)
        assertEquals(35.5678, entity.mapY)
    }

    @Test
    fun `축제 기간을 새 엔티티로 옮긴다`() {
        val entity = CatalogFestivalPeriod(
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 10, 3),
        ).toNewEntity(catalogId = 5555)

        assertEquals(5555L, entity.catalogId)
        assertEquals(LocalDate.of(2026, 10, 1), entity.eventStartDate)
        assertEquals(LocalDate.of(2026, 10, 3), entity.eventEndDate)
    }

    @Test
    fun `축제 엔티티를 도메인으로 옮긴다`() {
        val period = SpotCatalogFestivalEntity(
            catalogId = 5555L,
            eventStartDate = LocalDate.of(2026, 10, 1),
            eventEndDate = LocalDate.of(2026, 10, 3),
        ).toDomain()

        assertEquals(LocalDate.of(2026, 10, 1), period.startDate)
        assertEquals(LocalDate.of(2026, 10, 3), period.endDate)
    }

    @Test
    fun `왕복해도 좌표가 서로 섞이지 않는다`() {
        val restored = entity().toDomain().toNewEntity()

        assertEquals(129.1234, restored.mapX)
        assertEquals(35.5678, restored.mapY)
    }
}
