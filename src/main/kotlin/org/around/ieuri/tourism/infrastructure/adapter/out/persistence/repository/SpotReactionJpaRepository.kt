package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotReactionEntity
import org.springframework.data.jpa.repository.JpaRepository

/** 좋아요/싫어요 Spring Data 레포지토리. 재평가는 자연키 (member_id, catalog_id, language)로 찾는다(SPEC-0029 R-13). */
interface SpotReactionJpaRepository : JpaRepository<SpotReactionEntity, Long> {
    fun findByMemberIdAndCatalogIdAndLanguage(
        memberId: Long,
        catalogId: Long,
        language: DomainLocale,
    ): SpotReactionEntity?

    /** 탈퇴 회원의 기록 전량 삭제(SPEC-0011 R17). */
    fun deleteByMemberId(memberId: Long): Long
}
