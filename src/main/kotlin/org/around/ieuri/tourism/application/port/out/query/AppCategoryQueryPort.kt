package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.tourism.application.query.AppCategoryView
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.domain.vo.DomainLocale

/** 카테고리 구성·표시 정보 읽기 포트(SPEC-0020). `category` 계열 테이블만 본다. */
interface AppCategoryQueryPort {
    /**
     * 이 앱·이 언어에 켜져 있는 카테고리를 순서대로. 구성이 없으면 빈 목록이며 오류가 아니다 —
     * 알 수 없는 앱 키가 그대로 도착할 수 있기 때문이다.
     */
    fun findCategories(appKey: AppKey, language: DomainLocale): List<AppCategoryView>

    /**
     * 카테고리 코드 → 표시 정보를 **한 번에**. 목록 응답이 행마다 묻지 않게 한다.
     *
     * 정의가 없는 코드는 결과에 키가 없다 — 호출자가 코드 자체를 라벨로 쓰는 폴백을 택한다
     * ([CategoryDisplay.fallback]). 없는 카테고리라고 카드를 감추지는 않는다.
     */
    fun findDisplays(codes: Set<String>, language: DomainLocale): Map<String, CategoryDisplay>
}
