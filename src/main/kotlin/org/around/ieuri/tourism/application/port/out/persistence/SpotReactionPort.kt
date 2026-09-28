package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.domain.vo.DomainLocale

/** 좋아요/싫어요 저장 포트. 식별 단위는 (memberId, catalogId, language)이다(SPEC-0029 R-13). */
interface SpotReactionPort {
    /** [language]까지 맞는 기존 기록. 없으면 null. */
    fun find(memberId: Long, catalogId: Long, language: DomainLocale): SpotReaction?

    /** 신규 생성 또는 재평가 저장(upsert). */
    fun save(reaction: SpotReaction)

    /** 카탈로그가 실재하는지. 언어와 무관하다. */
    fun existsCatalog(catalogId: Long): Boolean

    /** 탈퇴 회원의 기록 전량 삭제. 지운 행 수를 돌려준다. */
    fun deleteByMember(memberId: Long): Long
}
