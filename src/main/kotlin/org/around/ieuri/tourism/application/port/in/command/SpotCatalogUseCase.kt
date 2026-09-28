package org.around.ieuri.tourism.application.port.`in`.command

import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 카탈로그 배정 유스케이스(SPEC-0019). 배치가 호출한다.
 * - 언어 슬롯은 원본이 있는 언어에만 생긴다. 번역을 채우지 않는다(SPEC-0029).
 */
interface SpotCatalogUseCase {

    /**
     * 미배정 spot(`catalog_id`가 null인 행)을 카탈로그에 배정한다. 배정된 행은 다시 보지 않는다.
     * - 기존 카탈로그와 같은 장소가 뒤늦게 생겨도 병합하지 않는다.
     */
    fun assignCatalogs(language: DomainLocale, afterSpotId: Long = 0L): CatalogAssignmentResult

    /**
     * 원본이 사라진 카탈로그를 조회에서 감추고, 돌아온 것은 다시 보이게 한다.
     * - 배정 뒤에 부른다. 앞에 두면 방금 들어온 장소를 잠시 감춘다.
     */
    fun refreshCatalogVisibility()
}

/** 배정 결과. [joined]는 기존 카탈로그에 붙은 행, [catalogsCreated]는 새 카탈로그를 만든 행이다. */
data class CatalogAssignmentResult(
    val joined: Int,
    val catalogsCreated: Int,
    /**
     * 표시할 제목이 없어 배정하지 않은 행. 미배정으로 남아 다음 회차에 다시 조회된다.
     * - 언어 소진 판정은 [assigned]가 아니라 [processed]로 한다.
     */
    val skipped: Int = 0,
    /** 이번 회차가 읽은 마지막 spot id. 다음 회차의 키셋 커서다. */
    val lastSpotId: Long = 0L,
) {
    val assigned: Int get() = joined + catalogsCreated

    /** 이 회차가 무언가 읽었는가. 언어 소진 판정의 기준이다. */
    val processed: Int get() = assigned + skipped
}
