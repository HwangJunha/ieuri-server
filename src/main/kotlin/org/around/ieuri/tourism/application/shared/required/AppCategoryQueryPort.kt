package org.around.ieuri.tourism.application.shared.required

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.tourism.application.shared.provided.dto.AppCategoryView
import org.around.ieuri.tourism.application.shared.provided.dto.CategoryDisplay
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 카테고리 구성·표시 정보 읽기 포트(SPEC-0020). `category` 계열 테이블만 본다. */
interface AppCategoryQueryPort {
    /** 이 앱·이 언어에 켜져 있는 카테고리를 순서대로. 구성이 없으면 오류가 아니라 빈 목록이다. */
    fun findCategories(appKey: AppKey, language: DomainLocale): List<AppCategoryView>

    /** 카테고리 코드 → 표시 정보를 한 번에. 정의가 없는 코드는 결과에 키가 없다. */
    fun findDisplays(codes: Set<String>, language: DomainLocale): Map<String, CategoryDisplay>

    /** 정의된 카테고리인가. 코드가 캐시 키가 되는 상세 하위 필드에만 쓰고, 목록 필터에는 쓰지 않는다. */
    fun exists(code: String): Boolean
}
