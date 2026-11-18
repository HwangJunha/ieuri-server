package org.around.ieuri.tourism.domain.catalog.event

/** 이벤트가 싣는 (카탈로그, 언어) 한 쌍. 언어는 도메인 간 계약이라 문자열 코드로 싣는다. */
data class CatalogLanguageRef(
    val catalogId: Long,
    val languageCode: String,
)
