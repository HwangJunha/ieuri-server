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

    /** 카탈로그가 실재하는지. 반응을 붙일 대상 유효성 판정이다. */
    fun existsCatalog(catalogId: Long): Boolean

    /** 탈퇴 회원의 기록 전량 삭제(SPEC-0011 R17). 지운 행 수를 돌려준다. */
    fun deleteByMember(memberId: Long): Long
}
