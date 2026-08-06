package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotReactionEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 좋아요/싫어요 Spring Data 레포지토리. PK는 surrogate id(Long).
 * upsert 판정·재평가는 자연키 (member_id, spot_id) 파생 조회로 관리 엔티티를 찾는다.
 */
interface SpotReactionJpaRepository : JpaRepository<SpotReactionEntity, Long> {
    fun findByMemberIdAndSpotId(memberId: Long, spotId: Long): SpotReactionEntity?

    /** 탈퇴 회원의 기록 전량 삭제(SPEC-0011 R17). */
    fun deleteByMemberId(memberId: Long): Long
}
