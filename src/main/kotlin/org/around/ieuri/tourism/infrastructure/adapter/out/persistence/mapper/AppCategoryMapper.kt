package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.tourism.application.query.AppCategoryView
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.AppCategoryEntity

/**
 * 엔티티 → 노출 카테고리 read DTO. `enabled`·`appKey`·`language`는 조회 조건이라 담지 않는다.
 *
 * 표시 정보는 `category` 계열 테이블에 있으므로 밖에서 받는다 — 매퍼는 순수 함수로 남는다
 * (POL-033). 정의가 없는 코드는 코드 자체를 라벨로 쓰는 폴백으로 떨어진다.
 */
fun AppCategoryEntity.toView(display: CategoryDisplay?): AppCategoryView {
    val code = SpotCategory(category)
    val resolved = display ?: CategoryDisplay.fallback(code)
    return AppCategoryView(
        category = code,
        label = resolved.label,
        iconUrl = resolved.iconUrl,
        position = position.toInt(),
    )
}
