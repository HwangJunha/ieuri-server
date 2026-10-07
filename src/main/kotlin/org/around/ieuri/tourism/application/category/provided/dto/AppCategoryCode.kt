package org.around.ieuri.tourism.application.category.provided.dto

/** 같은 구성을 문자열로 표현한 read DTO. 다른 도메인이 카테고리 구성을 읽을 때 쓴다. */
data class AppCategoryCode(
    val category: String,
    val label: String,
    val iconUrl: String?,
)
