package org.around.ieuri.tourism.application.catalog.provided

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.OffsetPage
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.MapViewportQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.MapViewportView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotSummaryView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotView
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotsQuery
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 카탈로그 조회(CQRS 읽기) — 주변 목록, 좋아요 목록, 지도 뷰포트, 다른 도메인용 요약. 언어는 명시 파라미터로 받는다. */
interface SpotCatalogQueryUseCase {
    /**
     * 위치 기반 관광지 오프셋 페이지. 거리 오름차순.
     * - [memberId]가 있으면 이미 반응한 관광지를 제외하고 좌표 조회 사실을 남긴다(SPEC-0032 R19). null은 비로그인이다.
     */
    fun spots(query: SpotsQuery, locale: DomainLocale, memberId: Long?): OffsetPage<SpotView>

    /**
     * 회원이 좋아요한 spot 한 페이지. likedAt 내림차순, 동률은 spot.id.
     * - 요청 언어로 한정하며 인증된 [memberId] 본인 것만 조회한다.
     */
    fun likedSpots(query: LikedSpotsQuery, locale: DomainLocale, memberId: Long): CursorPage<LikedSpotView>

    /**
     * 좋아요한 spot 중 검색어에 일치하는 한 페이지(SPEC-0033). 정렬·커서는 [likedSpots]와 같다.
     * - 검색 노드(복제본)를 보므로 방금 누른 좋아요가 아직 없을 수 있다.
     * - 검증 실패는 EMPTY_QUERY·QUERY_TOO_LONG.
     */
    fun searchLikedSpots(query: LikedSpotsQuery, text: String, locale: DomainLocale, memberId: Long): CursorPage<LikedSpotView>

    /**
     * 뷰포트 안의 spot·문화행사 마커(SPEC-0014 R1~R3, R12).
     * - 상한을 넘으면 잘리고 잘림 플래그가 선다.
     */
    fun mapViewport(query: MapViewportQuery, locale: DomainLocale): MapViewportView

    /**
     * 카탈로그가 보유한 최소 식별 정보. upstream을 호출하지 않는다. 다른 도메인이 쓰는 진입점이다.
     * - [languageCode]에서 해소되지 않으면 예외가 아니라 null이다(SPEC-0029 R-24). 모르는 언어 코드는 폴백 언어로 읽는다.
     */
    fun spotSummary(catalogId: Long, languageCode: String): SpotSummaryView?

    /** 여러 카탈로그 요약을 한 번에. [languageCode]에서 해소되지 않는 id는 결과에 키가 없다. */
    fun spotSummaries(catalogIds: List<Long>, languageCode: String): Map<Long, SpotSummaryView>
}
