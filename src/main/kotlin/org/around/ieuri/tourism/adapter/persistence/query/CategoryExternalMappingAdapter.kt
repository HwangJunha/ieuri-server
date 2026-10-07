package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.adapter.persistence.table.CategoryExternalMappingTable
import org.around.ieuri.tourism.adapter.persistence.table.CategoryTable
import org.around.ieuri.tourism.application.category.required.CategoryExternalMappingPort
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

/** 외부 코드 매핑 조회(V59). `category`와 조인해 우리 코드를 함께 읽는다. 캐시는 두지 않는다. */
@Component
class CategoryExternalMappingAdapter : CategoryExternalMappingPort {

    private val mappingWithCategory = CategoryExternalMappingTable
        .join(CategoryTable, JoinType.INNER, CategoryExternalMappingTable.categoryId, CategoryTable.id)
    override fun externalCode(category: SpotCategory, provider: String, codeset: String): String? = mappingWithCategory
        .select(CategoryExternalMappingTable.externalCode)
        .where {
            (CategoryTable.code eq category.code) and
                (CategoryExternalMappingTable.provider eq provider) and
                (CategoryExternalMappingTable.codeset eq codeset)
        }
        .limit(1).singleOrNull()
        ?.get(CategoryExternalMappingTable.externalCode)
    override fun categoryOf(externalCode: String, provider: String, codeset: String): SpotCategory? = mappingWithCategory
        .select(CategoryTable.code)
        .where {
            (CategoryExternalMappingTable.externalCode eq externalCode) and
                (CategoryExternalMappingTable.provider eq provider) and
                (CategoryExternalMappingTable.codeset eq codeset)
        }
        .limit(1).singleOrNull()
        ?.let { SpotCategory(it[CategoryTable.code]) }
}
