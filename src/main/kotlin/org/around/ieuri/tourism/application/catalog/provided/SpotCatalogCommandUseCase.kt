package org.around.ieuri.tourism.application.catalog.provided

import org.around.ieuri.tourism.application.catalog.provided.dto.CatalogAssignmentResult
import org.around.ieuri.tourism.application.catalog.provided.dto.CatalogSplitResult
import org.around.ieuri.tourism.domain.shared.DomainLocale

/**
 * 카탈로그 배정·분할·노출 갱신(SPEC-0019·SPEC-0029). 배치가 호출한다.
 * - 언어 슬롯은 원본이 있는 언어에만 생긴다. 번역을 채우지 않는다(SPEC-0029).
 */
interface SpotCatalogCommandUseCase {

    /**
     * 미배정 spot(`catalog_id`가 null인 행)을 카탈로그에 배정한다. 배정된 행은 다시 보지 않는다.
     * - 기존 카탈로그와 같은 장소가 뒤늦게 생겨도 병합하지 않는다.
     */
    fun assignCatalogs(language: DomainLocale, afterSpotId: Long = 0L): CatalogAssignmentResult

    /**
     * 서로 다른 장소가 접힌 카탈로그를 찾아 가른다.
     * - 배정이 전부 끝난 뒤, 노출 갱신보다 앞에 부른다.
     */
    fun splitMismatchedCatalogs(): CatalogSplitResult

    /**
     * 원본이 사라진 카탈로그를 조회에서 감추고, 돌아온 것은 다시 보이게 한다.
     * - 배정 뒤에 부른다. 앞에 두면 방금 들어온 장소를 잠시 감춘다.
     */
    fun refreshCatalogVisibility()
}
