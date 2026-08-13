package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.specification.ReactionRow

/**
 * 좋아요 dedupe 포트(SPEC-0019 §4.2). 참조 축 이전 중 한 번만 쓰인다.
 *
 * **제약 교체(V54) 전에 반드시 선행한다** — 한 회원이 같은 장소를 여러 언어로 좋아요했다면
 * 카탈로그 축에서 최대 4행이 하나로 충돌하고, 사전 dedupe 없이 `ADD CONSTRAINT`를 치면 실패한다.
 */
interface ReactionDedupePort {

    /**
     * `(member_id, catalog_id)`가 중복인 묶음만 돌려준다. 충돌하지 않는 행은 대상이 아니다.
     *
     * 한 번에 [limit]개 묶음까지. 전량을 메모리에 올리지 않기 위한 분할이다.
     */
    fun findConflicting(limit: Int): List<ReactionRow>

    /** 지울 행. 남길 행은 건드리지 않는다. */
    fun deleteByIds(ids: List<Long>): Int
}
