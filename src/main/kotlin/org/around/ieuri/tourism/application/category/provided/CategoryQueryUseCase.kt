package org.around.ieuri.tourism.application.category.provided

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.tourism.application.category.provided.dto.AppCategoryView
import org.around.ieuri.tourism.application.category.provided.dto.CategoryDisplay
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 앱별 카테고리 구성과 표시 정보 조회(SPEC-0020). */
interface CategoryQueryUseCase {
    /**
     * 이 앱·이 언어에 노출되는 카테고리를 구성된 순서대로. 출처는 `app_category`.
     * - 구성이 없으면 오류가 아니라 빈 목록이다.
     */
    fun availableCategories(appKey: AppKey, locale: DomainLocale): List<AppCategoryView>

    /**
     * 카테고리 코드 → 표시 정보를 한 번에.
     * - 정의가 없는 코드도 키가 있고, 코드 자체를 라벨로 쓰는 폴백이 담긴다.
     */
    fun categoryDisplays(codes: Set<String>, locale: DomainLocale): Map<String, CategoryDisplay>
}
