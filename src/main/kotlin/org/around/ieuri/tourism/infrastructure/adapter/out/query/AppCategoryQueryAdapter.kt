package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.tourism.application.port.out.query.AppCategoryQueryPort
import org.around.ieuri.tourism.application.query.AppCategoryView
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toView
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.AppCategoryJpaRepository
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.CategoryJpaRepository
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.CategoryTranslationJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 앱별 카테고리 구성 조회(SPEC-0020). 정렬·필터가 인덱스와 짝이라 파생 쿼리로 충분하다. */
@Component
class AppCategoryQueryAdapter(
    private val repository: AppCategoryJpaRepository,
    private val categoryRepository: CategoryJpaRepository,
    private val translationRepository: CategoryTranslationJpaRepository,
) : AppCategoryQueryPort {

    @Transactional(readOnly = true)
    override fun findCategories(appKey: AppKey, language: DomainLocale): List<AppCategoryView> {
        val configs = repository
            .findByAppKeyAndLanguageAndEnabledIsTrueOrderByPositionAscIdAsc(appKey.value, language)
        val displays = findDisplays(configs.map { it.category }.toSet(), language)
        return configs.map { it.toView(displays[it.category]) }
    }

    /**
     * 코드별 표시 정보를 한 번에. 정의가 없는 코드는 결과에 키가 없다 — 호출자가 폴백을 택한다.
     *
     * 라벨은 요청 언어의 번역이며, 번역이 없으면 코드를 그대로 쓴다.
     */
    @Transactional(readOnly = true)
    override fun findDisplays(
        codes: Set<String>,
        language: DomainLocale,
    ): Map<String, CategoryDisplay> {
        if (codes.isEmpty()) return emptyMap()
        val labels = translationRepository
            .findByCategoryCodeInAndLanguage(codes, language)
            .associate { it.categoryCode to it.label }
        return categoryRepository.findByCodeIn(codes).associate { category ->
            category.code to CategoryDisplay(
                category = SpotCategory(category.code),
                label = labels[category.code] ?: category.code,
                iconUrl = category.iconUrl,
            )
        }
    }
}
