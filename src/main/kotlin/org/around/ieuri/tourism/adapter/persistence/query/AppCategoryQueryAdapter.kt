package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.tourism.adapter.persistence.mapper.toAppCategoryView
import org.around.ieuri.tourism.adapter.persistence.mapper.toCategoryDisplay
import org.around.ieuri.tourism.adapter.persistence.table.AppCategoryTable
import org.around.ieuri.tourism.adapter.persistence.table.CategoryTable
import org.around.ieuri.tourism.adapter.persistence.table.CategoryTranslationTable
import org.around.ieuri.tourism.application.shared.provided.dto.AppCategoryView
import org.around.ieuri.tourism.application.shared.provided.dto.CategoryDisplay
import org.around.ieuri.tourism.application.shared.required.AppCategoryQueryPort
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

/**
 * 카테고리 구성·표시 정보 조회(SPEC-0020).
 * - 라벨은 좌측 조인이다 — 번역이 없는 카테고리도 빠지지 않고 코드가 라벨이 된다.
 */
@Component
class AppCategoryQueryAdapter : AppCategoryQueryPort {

    /** 이 앱·이 언어에 켜진 카테고리. 배열 순서는 보장하지 않는다(V59) — 코드 오름차순으로만 고정한다. */
    override fun findCategories(appKey: AppKey, language: DomainLocale): List<AppCategoryView> = AppCategoryTable
        .join(CategoryTable, JoinType.INNER, AppCategoryTable.categoryId, CategoryTable.id)
        .withTranslation(language)
        .select(CategoryTable.code, CategoryTranslationTable.label, CategoryTable.iconUrl)
        .where {
            (AppCategoryTable.appKey eq appKey.value) and
                (AppCategoryTable.language eq language.name) and
                (AppCategoryTable.active eq true)
        }
        .orderBy(CategoryTable.code, SortOrder.ASC)
        .map { it.toAppCategoryView() }

    /** 코드별 표시 정보를 한 번에. 정의가 없는 코드는 결과에 키가 없다. */
    override fun findDisplays(codes: Set<String>, language: DomainLocale): Map<String, CategoryDisplay> {
        if (codes.isEmpty()) return emptyMap()
        return CategoryTable.withTranslation(language)
            .select(CategoryTable.code, CategoryTranslationTable.label, CategoryTable.iconUrl)
            .where { CategoryTable.code inList codes }
            .associate { it[CategoryTable.code] to it.toCategoryDisplay() }
    }
    override fun exists(code: String): Boolean = CategoryTable.select(CategoryTable.id)
        .where { CategoryTable.code eq code }
        .limit(1).any()

    /** 그 언어 라벨 좌측 조인. 라벨 열은 null일 수 있다. */
    private fun org.jetbrains.exposed.v1.core.ColumnSet.withTranslation(language: DomainLocale) = join(
        CategoryTranslationTable,
        JoinType.LEFT,
        additionalConstraint = {
            (CategoryTranslationTable.categoryId eq CategoryTable.id) and (CategoryTranslationTable.language eq language.name)
        },
    )
}
