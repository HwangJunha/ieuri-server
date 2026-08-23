package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotCatalogTranslationEntity
import org.springframework.data.jpa.repository.JpaRepository

/** 카탈로그 Spring Data 레포지토리. PK는 surrogate id(Long). */
interface SpotCatalogJpaRepository : JpaRepository<SpotCatalogEntity, Long>

/**
 * 카탈로그 번역 레포지토리. 갱신은 출처에 따라 갈리므로 upsert 어댑터가 담당하고,
 * 여기서는 신규 저장과 조회만 한다.
 */
interface SpotCatalogTranslationJpaRepository : JpaRepository<SpotCatalogTranslationEntity, Long> {
    fun findByCatalogId(catalogId: Long): List<SpotCatalogTranslationEntity>
}
