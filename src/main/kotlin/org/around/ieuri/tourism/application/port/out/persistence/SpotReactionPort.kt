package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 좋아요/싫어요 저장 아웃바운드 포트. **식별 단위는 (memberId, catalogId, language)**
 * (SPEC-0029 R-13) — 활성 언어별로 따로 모인다. 재평가는 같은 키에 대한 upsert(덮어쓰기)이며
 * 이력을 남기지 않는다.
 */
interface SpotReactionPort {
    /**
     * 기존 기록을 로드(재평가 시 관리 엔티티에 도메인 변경을 얹기 위함). 없으면 null.
     * **[language]까지 맞는 행만 본다** — 다른 언어의 기록은 여기에 걸리지 않고 그 언어에 남는다.
     */
    fun find(memberId: Long, catalogId: Long, language: DomainLocale): SpotReaction?

    /** 신규 생성 또는 재평가 저장(upsert). */
    fun save(reaction: SpotReaction)

    /** 카탈로그가 실재하는지 — **언어와 무관하다.** 반응을 붙일 대상 유효성 판정이다. */
    fun existsCatalog(catalogId: Long): Boolean

    /** 탈퇴 회원의 기록 전량 삭제(SPEC-0011 R17). 지운 행 수를 돌려준다. */
    fun deleteByMember(memberId: Long): Long
}
