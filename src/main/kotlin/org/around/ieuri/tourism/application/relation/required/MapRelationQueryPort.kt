package org.around.ieuri.tourism.application.relation.required

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.relation.required.dto.MapRelationOverlap

/** 지도 관계의 읽기 포트(SPEC-0030). */
interface MapRelationQueryPort {

    /**
     * 내 관계 목록과 각 상대와의 겹침을 한 번의 질의로 계산한다. 닉네임은 호출자가 따로 묻는다.
     * - 정렬은 `created_at` 내림차순 + 상대 id 내림차순이다.
     * - 커서는 거르기 전의 읽어 온 행에서 붙는다. [after]를 읽지 못하면 INVALID_CURSOR다.
     */
    fun findRelations(
        memberId: Long,
        first: Int,
        after: String?,
    ): CursorPage<MapRelationOverlap>
}
