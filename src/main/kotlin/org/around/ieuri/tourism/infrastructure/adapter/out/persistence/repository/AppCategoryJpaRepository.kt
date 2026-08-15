package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.AppCategoryEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AppCategoryJpaRepository : JpaRepository<AppCategoryEntity, Long> {
    /** 이 앱·이 언어의 켜진 구성을 순서대로. 인덱스(app_key, language, position, id)와 짝이다. */
    fun findByAppKeyAndLanguageAndEnabledIsTrueOrderByPositionAscIdAsc(
        appKey: String,
        language: DomainLocale,
    ): List<AppCategoryEntity>
}
