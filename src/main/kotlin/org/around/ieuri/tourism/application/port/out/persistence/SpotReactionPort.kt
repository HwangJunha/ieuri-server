package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.aggregate.SpotReaction

/**
 * 좋아요/싫어요 저장 아웃바운드 포트. **식별 단위는 (memberId, catalogId)**(SPEC-0019 R-10).
 * 재평가는 같은 키에 대한 upsert(덮어쓰기)이며 이력을 남기지 않는다.
 */
interface SpotReactionPort {
    /** 기존 기록을 로드(재평가 시 관리 엔티티에 도메인 변경을 얹기 위함). 없으면 null. */
    fun find(memberId: Long, catalogId: Long): SpotReaction?

    /** 신규 생성 또는 재평가 저장(upsert). */
    fun save(reaction: SpotReaction)

    /**
     * `spot.id` → 소속 카탈로그 id. 배정되지 않았거나 없는 spot이면 null이다.
     *
     * **클라이언트는 계속 `spot.id`를 보낸다**(계약 불변) — 서버가 내부에서 카탈로그로 해석한다.
     */
    fun findCatalogIdBySpotId(spotId: Long): Long?

    /** 탈퇴 회원의 기록 전량 삭제(SPEC-0011 R17). 지운 행 수를 돌려준다. */
    fun deleteByMember(memberId: Long): Long
}
