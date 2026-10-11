package org.around.ieuri.tourism.application.category

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.tourism.application.category.provided.CategoryMappingQueryUseCase
import org.around.ieuri.tourism.application.category.provided.CategoryQueryUseCase
import org.around.ieuri.tourism.application.category.provided.dto.AppCategoryCode
import org.around.ieuri.tourism.application.category.provided.dto.CategoryDisplay
import org.around.ieuri.tourism.application.category.required.AppCategoryQueryPort
import org.around.ieuri.tourism.application.category.required.CategoryExternalMappingPort
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 카테고리 조회 구현 — 앱별 구성·표시 정보와 외부 제공자 코드 매핑. */
@Service
@Transactional(readOnly = true)
class CategoryQueryService(
    private val appCategoryQueryPort: AppCategoryQueryPort,
    private val mappingPort: CategoryExternalMappingPort,
) : CategoryQueryUseCase,
    CategoryMappingQueryUseCase {

    override fun availableCategories(appKey: AppKey, locale: DomainLocale): List<CategoryDisplay> = appCategoryQueryPort.findCategories(appKey, locale)

    override fun availableCategoryCodes(appKey: AppKey, languageCode: String): List<AppCategoryCode> = availableCategories(appKey, DomainLocale.fromLanguageTag(languageCode))
        .map { AppCategoryCode(category = it.category.code, label = it.label, iconUrl = it.iconUrl) }

    /** 요청한 모든 코드에 값이 있다 — 표시 정보가 없는 코드는 폴백으로 채운다. */
    override fun categoryDisplays(codes: Set<String>, locale: DomainLocale): Map<String, CategoryDisplay> {
        val found = appCategoryQueryPort.findDisplays(codes, locale)
        return codes.associateWith { found[it] ?: SpotCategory(it).let { category -> CategoryDisplay(category, category.defaultLabel, iconUrl = null) } }
    }

    override fun externalCode(category: SpotCategory, provider: String, codeset: String): String? = mappingPort.externalCode(category, provider, codeset)

    override fun categoryOf(externalCode: String, provider: String, codeset: String): SpotCategory? = mappingPort.categoryOf(externalCode, provider, codeset)
}
