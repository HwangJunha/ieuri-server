package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.CategoryEntity
import org.springframework.data.jpa.repository.JpaRepository

/** 카테고리 정의 저장소. 행 단위 확인에만 쓰고, 목록 조회는 AppCategoryQueryAdapter가 한다. */
interface CategoryJpaRepository : JpaRepository<CategoryEntity, Long> {
    fun existsByCode(code: String): Boolean
}
