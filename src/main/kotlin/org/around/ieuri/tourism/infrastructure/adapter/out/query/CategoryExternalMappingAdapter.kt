package org.around.ieuri.tourism.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.query.CategoryExternalMappingPort
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QCategoryEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QCategoryExternalMappingEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 외부 코드 매핑 조회(V59). `category`와 조인해 우리 코드를 함께 읽는다.
 *
 * 캐시를 두지 않는다 — 행이 20개 안팎이고 `(provider, codeset, external_code)` 인덱스가 받치며,
 * 적재 배치는 페이지 단위로 돌아 행마다 묻지 않는다. 관측된 문제 없이 캐시를 먼저 얹지 않는다.
 */
@Component
class CategoryExternalMappingAdapter(
    private val queryFactory: JPAQueryFactory,
) : CategoryExternalMappingPort {

    private val mapping = QCategoryExternalMappingEntity.categoryExternalMappingEntity
    private val category = QCategoryEntity.categoryEntity

    @Transactional(readOnly = true)
    override fun externalCode(category: SpotCategory, provider: String, codeset: String): String? =
        queryFactory
            .select(mapping.externalCode)
            .from(mapping)
            .join(this.category).on(this.category.id.eq(mapping.categoryId))
            .where(
                this.category.code.eq(category.code),
                mapping.provider.eq(provider),
                mapping.codeset.eq(codeset),
            )
            .fetchFirst()

    @Transactional(readOnly = true)
    override fun categoryOf(externalCode: String, provider: String, codeset: String): SpotCategory? =
        queryFactory
            .select(category.code)
            .from(mapping)
            .join(category).on(category.id.eq(mapping.categoryId))
            .where(
                mapping.externalCode.eq(externalCode),
                mapping.provider.eq(provider),
                mapping.codeset.eq(codeset),
            )
            .fetchFirst()
            ?.let { SpotCategory(it) }
}
