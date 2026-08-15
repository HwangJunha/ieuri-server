package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.CategoryEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 카테고리 정의 저장소. 조회는 [org.around.ieuri.tourism.infrastructure.adapter.out.query.AppCategoryQueryAdapter]가
 * QueryDSL 프로젝션으로 하고, 이 저장소는 존재 확인처럼 행 단위로 묻는 경우에만 쓴다.
 */
interface CategoryJpaRepository : JpaRepository<CategoryEntity, Long> {
    fun existsByCode(code: String): Boolean
}
