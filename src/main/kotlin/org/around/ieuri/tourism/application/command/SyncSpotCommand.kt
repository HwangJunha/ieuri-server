package org.around.ieuri.tourism.application.command

import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 관광지 1건의 적재 입력(SPEC-0004 R-1). **쓰기 입력이므로 Command다** — 읽기 측 read DTO와 구분한다.
 * 수집 포트(`SpotSyncPort`)가 만들고 적재 포트(`SpotStorePort`)가 소비하므로, 두 포트 중 어느 쪽도
 * 소유하지 않도록 여기(application/command)에 둔다.
 *
 * 도메인 `Spot`을 그대로 쓰지 않는 이유는 `Spot`이 `distance`(요청 좌표 종속)와 `attributionText`
 * (copyright 파생)를 필수로 요구하는데 둘 다 적재 대상이 아니기 때문이다 — 적재 시점에는 기준 좌표
 * 자체가 없으므로 `Spot`을 조립할 수 없다.
 *
 * [contentTypeId]는 해석 이전의 TourAPI 원본 코드로 **적재 전용**이며, 카테고리 폴백 감지·교정에만 쓴다
 * (응답·로그 노출 금지 — SPEC-0002 R8).
 *
 * **[contentTypeId]는 필수다.** 전량 적재 실측(2026-08-03, 121,913건)에서 값이 빠진 항목이 한 건도
 * 없었고, 없으면 이 컬럼을 둔 목적(폴백 사후 질의·재해석 교정)이 성립하지 않는다. 값을 주지 않는
 * 항목은 적재 대상에서 제외한다 — 어댑터가 드랍하고, DB의 NOT NULL(V19)이 한 번 더 막는다.
 */
data class SyncSpotCommand(
    val contentId: String,
    val language: DomainLocale,
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val coordinates: Coordinates,
    val category: SpotCategory,
    val contentTypeId: Int,
    val copyright: CopyrightType,
) {
    init {
        require(contentId.isNotBlank()) { "contentId는 필수입니다." }
        require(title.isNotBlank()) { "title은 필수입니다." }
        // 대표이미지 없는 항목은 적재 자체를 하지 않는다 — Spot.thumbnailImageUrl: String! 의 보장이
        // 요청 시점 필터에서 쓰기 시점 규율로 옮겨왔기 때문이다(런타임 계약 위험 1번).
        // 도메인 Spot.init의 같은 규칙과 짝을 이룬다: 적재 시점에 막고, 조립 시점에 다시 확인한다.
        require(thumbnailImageUrl.startsWith("https://")) { "thumbnailImageUrl은 https여야 합니다." }
    }
}
