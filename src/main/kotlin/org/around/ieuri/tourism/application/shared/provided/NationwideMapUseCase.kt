package org.around.ieuri.tourism.application.shared.provided

import org.around.ieuri.tourism.application.shared.provided.dto.CellImageView
import org.around.ieuri.tourism.application.shared.provided.dto.CellSpotView
import org.around.ieuri.tourism.application.shared.provided.dto.MapCellView
import org.around.ieuri.tourism.application.shared.provided.dto.SharedMapView
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 전국 지도 조회 유스케이스(SPEC-0030). 필드마다 메서드가 따로다. */
interface NationwideMapUseCase {

    /** 전체 칸. 시도 코드 → 칸 코드 오름차순이다. [memberId]가 null이면 모든 칸의 방문 여부가 false다. */
    fun mapCells(locale: DomainLocale, memberId: Long?): List<MapCellView>

    /** 칸 1개. 없는 칸이면 MAP_CELL_NOT_FOUND다. */
    fun mapCell(cellId: Long, locale: DomainLocale, memberId: Long?): MapCellView

    /** 모든 칸의 대표 이미지. 이미지가 없는 칸은 결과에 키가 없다. */
    fun allCellImages(): Map<Long, CellImageView>

    /** 칸 여러 개를 한 번에 읽는다. 없는 id는 오류 없이 빠지며, 순서는 인자 순서를 따른다. */
    fun mapCellsByIds(cellIds: Collection<Long>, locale: DomainLocale, memberId: Long?): List<MapCellView>

    /** 칸에 속한 장소. 좋아요 수 내림차순, 같은 수는 id 오름차순이며 [first]는 상한으로 절삭한다(POL-018). */
    fun cellSpots(cellId: Long, first: Int, locale: DomainLocale): List<CellSpotView>

    /** 공유 토큰으로 다른 회원의 지도를 본다. [viewerMemberId]가 있으면 겹치는 칸이 함께 오며, 차단 관계면 SHARE_LINK_BLOCKED다. */
    fun sharedMap(token: String, viewerMemberId: Long?): SharedMapView
}
