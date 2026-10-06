package org.around.ieuri.tourism.application.relation.required

import org.around.ieuri.tourism.application.relation.provided.dto.MapRelationOverlap
import org.around.ieuri.tourism.domain.shared.RelationCursor

/** 지도 관계의 읽기 포트(SPEC-0030). */
interface MapRelationQueryPort {

    /**
     * 내 관계 목록과 각 상대와의 겹침을 한 번의 질의로 계산한다. 닉네임은 호출자가 따로 묻는다.
     * - 정렬은 `created_at` 내림차순 + 상대 id 내림차순이다.
     * - `limit + 1`건을 읽어 호출자가 다음 페이지 존재를 판정한다.
     */
    fun findRelations(
        memberId: Long,
        limit: Int,
        after: RelationCursor?,
    ): List<MapRelationOverlap>
}
