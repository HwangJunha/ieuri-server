package org.around.ieuri.tourism.application.category.provided.dto

import org.around.ieuri.tourism.domain.category.SpotCategory

/** 앱·언어에 노출되는 카테고리 1건과 표시 정보(read DTO, SPEC-0020). 노출 순서는 담지 않는다. */
data class AppCategoryView(
    val category: SpotCategory,
    /** 요청 언어 표시 문구. 번역이 없으면 코드가 그대로 들어온다. */
    val label: String,
    /** 칩·마커 이미지. 설정되지 않았으면 null이다. */
    val iconUrl: String?,
)

/** 카테고리의 표시 정보(read DTO). 목록·지도 응답이 카테고리를 그릴 때 쓴다. */
data class CategoryDisplay(
    val category: SpotCategory,
    val label: String,
    val iconUrl: String?,
) {
    companion object {
        /** 번역·정의가 없는 코드의 표현. 코드를 그대로 라벨로 쓴다. */
        fun fallback(category: SpotCategory) = CategoryDisplay(category = category, label = category.code, iconUrl = null)
    }
}
