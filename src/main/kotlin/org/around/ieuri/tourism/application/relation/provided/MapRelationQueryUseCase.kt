package org.around.ieuri.tourism.application.relation.provided

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationView
import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationsQuery

/** 지도 관계 조회 use case(SPEC-0030). */
interface MapRelationQueryUseCase {

    /** 내 관계 목록. 겹침은 조회 시점에 재계산되며, 정렬은 등록 시각 내림차순 고정이다. */
    fun mapRelations(query: MapRelationsQuery, memberId: Long): CursorPage<MapRelationView>
}
