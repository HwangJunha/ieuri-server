package org.around.ieuri.tourism.application.port.`in`.command

/** 서로 다른 장소가 접힌 카탈로그를 가르는 유스케이스(SPEC-0029). */
interface SpotCatalogSplitUseCase {

    /**
     * 서로 다른 장소가 접힌 카탈로그를 찾아 가른다.
     * - 배정이 전부 끝난 뒤, 노출 갱신보다 앞에 부른다.
     */
    fun splitMismatchedCatalogs(): CatalogSplitResult
}

/** 분할 결과. [catalogsSplit]은 갈라진 옛 카탈로그 수, [catalogsCreated]는 새로 만든 카탈로그 수다. */
data class CatalogSplitResult(
    val catalogsSplit: Int,
    val catalogsCreated: Int,
    val spotsMoved: Int,
)
