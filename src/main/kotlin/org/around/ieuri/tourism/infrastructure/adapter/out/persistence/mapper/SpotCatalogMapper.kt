package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.tourism.domain.aggregate.CatalogTranslation
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogTranslationEntity

/**
 * 카탈로그 엔티티를 도메인 [SpotCatalog]으로 옮긴다.
 *
 * 번역은 **파라미터로 받는다** — 엔티티에 연관을 걸지 않았으므로 조회는 호출자의 책임이고,
 * 매퍼는 순수 함수로 남는다.
 */
fun SpotCatalogEntity.toDomain(translations: List<SpotCatalogTranslationEntity>): SpotCatalog =
    SpotCatalog.restore(
        id = requireNotNull(id) { "저장된 카탈로그는 id를 갖는다." },
        representativeNameKo = representativeNameKo,
        nameSource = nameSource,
        translations = translations.associate { it.language to it.toDomain() },
    )

/** 번역 엔티티를 도메인 값으로. */
fun SpotCatalogTranslationEntity.toDomain(): CatalogTranslation =
    CatalogTranslation(title = title, source = source)

/** 도메인 카탈로그를 새 엔티티로. id는 DB가 채운다. */
fun SpotCatalog.toNewEntity(): SpotCatalogEntity =
    SpotCatalogEntity(
        representativeNameKo = representativeNameKo,
        nameSource = nameSource,
    )

/**
 * 도메인 카탈로그의 번역들을 새 엔티티 목록으로.
 *
 * [catalogId]를 받는 이유는 신규 카탈로그의 id가 저장 이후에야 정해지기 때문이다 —
 * 부모를 먼저 persist한 뒤 그 id로 자식을 만든다.
 */
fun SpotCatalog.toNewTranslationEntities(catalogId: Long): List<SpotCatalogTranslationEntity> =
    translations.map { (language, translation) ->
        translation.toNewEntity(catalogId, language)
    }

/** 번역 값 하나를 새 엔티티로. */
fun CatalogTranslation.toNewEntity(catalogId: Long, language: DomainLocale): SpotCatalogTranslationEntity =
    SpotCatalogTranslationEntity(
        catalogId = catalogId,
        language = language,
        title = title,
        source = source,
    )
