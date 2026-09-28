package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.tourism.domain.aggregate.CatalogFestivalPeriod
import org.around.ieuri.tourism.domain.aggregate.CatalogStandardValues
import org.around.ieuri.tourism.domain.aggregate.CatalogTranslation
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogFestivalEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogTranslationEntity

/** 카탈로그 엔티티 ↔ 도메인 변환(POL-033). 번역과 축제는 별도 테이블이라 [toDomain]이 밖에서 받는다. */
fun SpotCatalogEntity.toDomain(
    translations: Map<DomainLocale, CatalogTranslation> = emptyMap(),
    festival: SpotCatalogFestivalEntity? = null,
): SpotCatalog = SpotCatalog.restore(
    id = requireNotNull(id) { "저장된 카탈로그에는 id가 있어야 합니다." },
    standard = CatalogStandardValues(
        mapX = mapX,
        mapY = mapY,
        thumbnailImageUrl = thumbnailImageUrl,
        category = SpotCategory(category),
        copyright = copyright,
    ),
    translations = translations,
    festivalPeriod = festival?.toDomain(),
)

/** 번역 엔티티 하나를 도메인 값으로. */
fun SpotCatalogTranslationEntity.toDomain(): CatalogTranslation = CatalogTranslation(title = title, address = address)

/** 축제 기간 엔티티를 도메인 값으로. */
fun SpotCatalogFestivalEntity.toDomain(): CatalogFestivalPeriod = CatalogFestivalPeriod(startDate = eventStartDate, endDate = eventEndDate)

/** 도메인 카탈로그를 새 엔티티로(id는 DB가 채운다). 번역은 [toNewTranslationEntities]가 만든다. */
fun SpotCatalog.toNewEntity(): SpotCatalogEntity = SpotCatalogEntity(
    mapX = standard.mapX,
    mapY = standard.mapY,
    thumbnailImageUrl = standard.thumbnailImageUrl,
    category = standard.category.code,
    copyright = standard.copyright,
)

/**
 * 도메인 카탈로그의 번역들을 새 엔티티 목록으로. [catalogId]는 부모를 persist한 뒤의 id다.
 * - 카탈로그의 좌표를 번역 행에 함께 심는다 — 비면 그 카탈로그는 검색에서 사라진다.
 */
fun SpotCatalog.toNewTranslationEntities(catalogId: Long): List<SpotCatalogTranslationEntity> = translations.map { (language, translation) ->
    translation.toNewEntity(catalogId, language, standard.mapX, standard.mapY, standard.category)
}

/** 번역 값 하나를 새 엔티티로. 좌표는 카탈로그 표준값의 사본이라 밖에서 받는다. */
fun CatalogTranslation.toNewEntity(
    catalogId: Long,
    language: DomainLocale,
    mapX: Double,
    mapY: Double,
    category: SpotCategory,
): SpotCatalogTranslationEntity = SpotCatalogTranslationEntity(
    catalogId = catalogId,
    language = language,
    title = title,
    address = address,
    mapX = mapX,
    mapY = mapY,
    category = category.code,
    // 그 언어의 spot에서 만드는 중이라 정의상 그 언어에서 해소된다.
    visible = true,
)

/** 축제 기간을 새 엔티티로. */
fun CatalogFestivalPeriod.toNewEntity(catalogId: Long): SpotCatalogFestivalEntity = SpotCatalogFestivalEntity(
    catalogId = catalogId,
    eventStartDate = startDate,
    eventEndDate = endDate,
)
