package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.adapter.persistence.table.CategoryExternalMappingTable
import org.around.ieuri.tourism.adapter.persistence.table.CategoryTable
import org.around.ieuri.tourism.application.shared.required.CategoryExternalMappingPort
import org.around.ieuri.tourism.domain.shared.SpotCategory
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 외부 코드 매핑 조회(V59). `category`와 조인해 우리 코드를 함께 읽는다. 캐시는 두지 않는다.
 * - 자기 트랜잭션을 연다 — TourAPI 클라이언트가 요청을 만들며 부르고, 사전 수집은 그 호출을 코루틴 스레드에서 돌려 호출자 트랜잭션이 없다.
 */
@Component
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
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
