package org.around.ieuri.tourism.application.catalog.required.dto

/**
 * 노출 갱신의 결과. [visible]은 갱신 후 노출 중인 (카탈로그, 언어) 전부 — 구독자가 받는 정본이다.
 * - [hiddenCount]·[shownCount]는 이번 회차에 뒤집힌 행 수로, 로그·관측용이다.
 */
data class VisibilityRefreshResult(
    val hiddenCount: Int,
    val shownCount: Int,
    val visible: List<CatalogLanguage>,
)
