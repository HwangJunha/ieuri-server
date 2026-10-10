package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.DomainLocale

/**
 * 회원의 관광지 좋아요/싫어요 기록. 식별은 (memberId, catalogId, language)다(SPEC-0029 R-13).
 * - 재평가는 같은 키에 덮어쓰며 이력을 남기지 않는다. 취소는 없다.
 */
data class SpotReaction(
    val memberId: Long,
    val catalogId: Long,
    val language: DomainLocale,
    val reaction: ReactionType,
) {
    init {
        require(memberId > 0) { "memberId는 양수여야 합니다." }
        require(catalogId > 0) { "catalogId는 양수여야 합니다." }
    }
}
