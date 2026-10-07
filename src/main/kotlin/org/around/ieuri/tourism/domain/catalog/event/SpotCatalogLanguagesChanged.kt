package org.around.ieuri.tourism.domain.catalog.event

/**
 * 카탈로그가 어떤 언어에서 해소되는지가 갱신되었다(SPEC-0029 R-15·R-23). 구독자가 자기 사본을 맞춘다.
 * - [resolved]는 델타가 아니라 정본이다 — 지금 노출 중인 (카탈로그, 언어) 전부다.
 * - 구독자는 이 값으로 자기 표를 통째로 맞춘다. 한 번에 수만 건이 실릴 수 있다(배치 경로 전용).
 */
data class SpotCatalogLanguagesChanged(
    val resolved: List<CatalogLanguageRef>,
)
