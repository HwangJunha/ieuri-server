package org.around.ieuri.tourism.application.category.provided.dto

import org.around.ieuri.tourism.domain.category.SpotCategory

/** 카테고리 1건의 표시 정보(read DTO, SPEC-0020). 앱 구성 목록과 응답의 카테고리 표시에 함께 쓴다. */
data class CategoryDisplay(
    val category: SpotCategory,
    /** 요청 언어 표시 문구. 번역이 없으면 코드가 그대로 들어온다. */
    val label: String,
    /** 칩·마커 이미지. 설정되지 않았으면 null이다. */
    val iconUrl: String?,
)
