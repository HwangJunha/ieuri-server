package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.query.MapRelationView
import org.around.ieuri.tourism.application.query.MapRelationsQuery

/** 지도 관계 조회 use case(SPEC-0030 확장). */
interface MapRelationQueryUseCase {

    /**
     * 내 관계 목록. **겹침은 조회 시점에 재계산된다** — 저장된 값이 아니다.
     *
     * 정렬은 등록 시각 내림차순 고정이다. 겹침 수로 정렬하지 않는 이유는 그 값이 조회마다
     * 바뀌어 커서가 불안정해지기 때문이다(§9).
     */
    fun mapRelations(query: MapRelationsQuery, memberId: Long): CursorPage<MapRelationView>
}
