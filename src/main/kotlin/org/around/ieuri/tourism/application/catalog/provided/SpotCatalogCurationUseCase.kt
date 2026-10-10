package org.around.ieuri.tourism.application.catalog.provided

import org.around.ieuri.tourism.application.catalog.provided.dto.CatalogAssignmentResult
import org.around.ieuri.tourism.application.catalog.provided.dto.CatalogSplitResult
import org.around.ieuri.tourism.application.catalog.provided.dto.DistrictAssignmentResult
import org.around.ieuri.tourism.domain.shared.DomainLocale

/**
 * 카탈로그 정비(SPEC-0019·SPEC-0029·SPEC-0030 R11). 수집 배치가 원본을 맞춘 뒤 부른다. 호출 하나가 트랜잭션 하나다.
 * - 언어 슬롯은 원본이 있는 언어에만 생긴다. 번역을 채우지 않는다(SPEC-0029).
 */
interface SpotCatalogCurationUseCase {

    /**
     * 미배정 원본(`catalog_id`가 null인 행)을 한 회차 분량만 카탈로그에 붙인다. 붙은 원본은 다시 보지 않는다.
     * - [afterSpotId] 이후만 읽는다(키셋). 건너뛴 원본은 미배정으로 남아 커서 없이는 전진하지 못한다.
     * - KO를 전량 소진한 뒤 다른 언어를 부른다 — 뒤집히면 같은 장소가 두 카탈로그로 갈린다.
     */
    fun assignCatalogs(language: DomainLocale, afterSpotId: Long): CatalogAssignmentResult

    /** 서로 다른 장소가 접힌 카탈로그를 찾아 가른다. 배정이 전부 끝난 뒤, 노출 갱신보다 앞에 부른다. */
    fun splitMismatchedCatalogs(): CatalogSplitResult

    /**
     * 원본이 사라진 번역을 감추고 돌아온 것은 되살린 뒤, 노출 중인 (카탈로그, 언어) 전부를 이벤트로 알린다.
     * - 배정 뒤에 부른다. 앞에 두면 방금 들어온 장소를 잠시 감춘다.
     */
    fun refreshCatalogVisibility()

    /** 시군구에 아직 귀속되지 않은 카탈로그를 좌표로 붙인다. 이미 귀속된 것은 건드리지 않는다. */
    fun assignDistricts(): DistrictAssignmentResult
}
