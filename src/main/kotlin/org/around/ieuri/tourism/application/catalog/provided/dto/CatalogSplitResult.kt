package org.around.ieuri.tourism.application.catalog.provided.dto

/** 분할 결과. [catalogsSplit]은 갈라진 옛 카탈로그 수, [catalogsCreated]는 새로 만든 카탈로그 수다. */
data class CatalogSplitResult(
    val catalogsSplit: Int,
    val catalogsCreated: Int,
    val spotsMoved: Int,
)
