package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.tourism.domain.aggregate.CatalogFestivalPeriod
import org.around.ieuri.tourism.domain.aggregate.CatalogStandardValues
import org.around.ieuri.tourism.domain.aggregate.CatalogTranslation
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogFestivalEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogTranslationEntity

/**
 * 카탈로그 엔티티 ↔ 도메인 변환(POL-033).
 *
 * 번역과 축제는 별도 테이블이라 [toDomain]이 밖에서 받는다 — 엔티티에 연관을 두지 않는
 * 프로젝트 규칙 때문이며, 조회 시점에 필요한 것만 읽어 오기 위해서다.
 */
fun SpotCatalogEntity.toDomain(
    translations: Map<DomainLocale, CatalogTranslation> = emptyMap(),
    festival: SpotCatalogFestivalEntity? = null,
): SpotCatalog =
    SpotCatalog.restore(
        id = requireNotNull(id) { "저장된 카탈로그에는 id가 있어야 합니다." },
        standard = CatalogStandardValues(
            mapX = mapX,
            mapY = mapY,
            thumbnailImageUrl = thumbnailImageUrl,
            category = category,
            copyright = copyright,
        ),
        translations = translations,
        festivalPeriod = festival?.toDomain(),
    )

/** 번역 엔티티 하나를 도메인 값으로. */
fun SpotCatalogTranslationEntity.toDomain(): CatalogTranslation =
    CatalogTranslation(title = title, address = address)

/** 축제 기간 엔티티를 도메인 값으로. */
fun SpotCatalogFestivalEntity.toDomain(): CatalogFestivalPeriod =
    CatalogFestivalPeriod(startDate = eventStartDate, endDate = eventEndDate)

/**
 * 도메인 카탈로그를 새 엔티티로. id는 DB가 채우므로 넘기지 않는다.
 *
 * 번역과 축제는 별도 엔티티라 여기서 만들지 않는다 — [toNewTranslationEntities]가 담당한다.
 */
fun SpotCatalog.toNewEntity(): SpotCatalogEntity =
    SpotCatalogEntity(
        mapX = standard.mapX,
        mapY = standard.mapY,
        thumbnailImageUrl = standard.thumbnailImageUrl,
        category = standard.category,
        copyright = standard.copyright,
    )

/**
 * 도메인 카탈로그의 번역들을 새 엔티티 목록으로.
 *
 * [catalogId]를 받는 이유는 신규 카탈로그의 id가 저장 이후에야 정해지기 때문이다 —
 * 부모를 먼저 persist한 뒤 그 id로 자식을 만든다.
 *
 * **카탈로그의 좌표를 번역 행에 함께 심는다.** 검색은 평면 질의라 조인을 받지 못해
 * 이 사본만 보며, 비면 그 카탈로그는 검색에서 사라진다.
 */
fun SpotCatalog.toNewTranslationEntities(catalogId: Long): List<SpotCatalogTranslationEntity> =
    translations.map { (language, translation) ->
        translation.toNewEntity(catalogId, language, standard.mapX, standard.mapY)
    }

/** 번역 값 하나를 새 엔티티로. 좌표는 카탈로그 표준값의 사본이라 밖에서 받는다. */
fun CatalogTranslation.toNewEntity(
    catalogId: Long,
    language: DomainLocale,
    mapX: Double,
    mapY: Double,
): SpotCatalogTranslationEntity =
    SpotCatalogTranslationEntity(
        catalogId = catalogId,
        language = language,
        title = title,
        address = address,
        mapX = mapX,
        mapY = mapY,
    )

/** 축제 기간을 새 엔티티로. PK가 catalogId라 upsert 단위이기도 하다. */
fun CatalogFestivalPeriod.toNewEntity(catalogId: Long): SpotCatalogFestivalEntity =
    SpotCatalogFestivalEntity(
        catalogId = catalogId,
        eventStartDate = startDate,
        eventEndDate = endDate,
    )
