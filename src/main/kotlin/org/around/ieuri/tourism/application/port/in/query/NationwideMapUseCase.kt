package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.query.CellImageView
import org.around.ieuri.tourism.application.query.CellSpotView
import org.around.ieuri.tourism.application.query.CellSpotsQuery
import org.around.ieuri.tourism.application.query.MapCellView
import org.around.ieuri.tourism.application.query.SharedMapView
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 전국 지도 조회 use case(SPEC-0030).
 *
 * **필드마다 메서드가 따로인 것이 의도다.** 대표 이미지와 spot 목록은 각각 별도 I/O이므로
 * 클라이언트가 그 필드를 고를 때만 호출되어야 한다 — 한 메서드가 전부를 모아 오면 필드 선택이
 * I/O를 바꾸지 못해 GraphQL을 쓰는 이유가 사라진다.
 */
interface NationwideMapUseCase {

    /**
     * 161칸 전체(R7). 정렬은 시도 코드 → 칸 코드 오름차순 고정이다(POL-019).
     *
     * [memberId]가 null이면 모든 칸의 방문 여부가 false다 — 비인증 조회가 허용된다(R6).
     */
    fun mapCells(locale: DomainLocale, memberId: Long?): List<MapCellView>

    /** 칸 1개. 없는 칸이면 MAP_CELL_NOT_FOUND다. */
    fun mapCell(cellId: Long, locale: DomainLocale, memberId: Long?): MapCellView

    /**
     * 모든 칸의 대표 이미지(R9). 칸 코드로 찾아 쓴다.
     *
     * **이미지가 없는 칸은 결과에 키가 없다**(R10) — 그 칸도 조회에서 빠지지 않으며 필드만
     * null이 된다. 칸이 161개로 고정된 작은 집합이라 부분 조회를 두지 않는다.
     */
    fun allCellImages(): Map<Long, CellImageView>

    /**
     * 칸 여러 개를 한 번에 읽는다. 겹친 칸의 이름을 보여줄 때 쓴다.
     *
     * 칸마다 [mapCell]을 부르지 않는다 — 겹침이 수십 칸이면 그만큼 질의가 나간다.
     * 없는 id는 결과에서 빠지며 오류가 아니다. 순서는 인자 순서를 따른다.
     */
    fun mapCellsByIds(cellIds: Collection<Long>, locale: DomainLocale, memberId: Long?): List<MapCellView>

    /** 칸에 속한 장소(R11). 좋아요 수 내림차순. */
    fun cellSpots(query: CellSpotsQuery, locale: DomainLocale): CursorPage<CellSpotView>

    /**
     * 공유 토큰으로 다른 회원의 지도를 본다(R13·R15·R16).
     *
     * [viewerMemberId]가 있으면 겹치는 칸이 함께 온다. 차단 관계면 SHARE_LINK_BLOCKED다.
     */
    fun sharedMap(token: String, viewerMemberId: Long?): SharedMapView
}
