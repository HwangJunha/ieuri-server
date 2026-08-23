package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.application.query.MapRelationOverlap
import org.around.ieuri.tourism.domain.vo.RelationCursor

/** 지도 관계의 읽기 포트(SPEC-0030 확장). */
interface MapRelationQueryPort {

    /**
     * 내 관계 목록과 **각 상대와의 겹침을 한 번의 질의로** 계산한다.
     *
     * 상대마다 방문 기록을 따로 읽으면 한 페이지(20명)에 왕복이 40번이 된다. 방문은 회원당
     * 최대 252행이고 칸은 161개라, 관계가 수백이어도 전체가 수만 행을 넘지 않아 한 문장
     * 집계로 끝난다.
     *
     * 정렬은 `created_at` 내림차순 + 상대 id 내림차순이다(§9) — 겹침 수는 조회마다 바뀌는
     * 값이라 커서의 정렬 키가 될 수 없다.
     *
     * `limit + 1`건을 읽어 호출자가 다음 페이지 존재를 판정한다.
     *
     * **닉네임은 여기서 오지 않는다** — auth 소유라 호출자가 따로 묻는다.
     */
    fun findRelations(
        memberId: Long,
        limit: Int,
        after: RelationCursor?,
    ): List<MapRelationOverlap>
}
