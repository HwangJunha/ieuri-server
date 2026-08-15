package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.CategoryEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.CategoryTranslationEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.CategoryTranslationId
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryJpaRepository : JpaRepository<CategoryEntity, String> {
    fun findByCodeIn(codes: Collection<String>): List<CategoryEntity>
}

interface CategoryTranslationJpaRepository :
    JpaRepository<CategoryTranslationEntity, CategoryTranslationId> {
    fun findByCategoryCodeInAndLanguage(
        categoryCodes: Collection<String>,
        language: DomainLocale,
    ): List<CategoryTranslationEntity>
}
