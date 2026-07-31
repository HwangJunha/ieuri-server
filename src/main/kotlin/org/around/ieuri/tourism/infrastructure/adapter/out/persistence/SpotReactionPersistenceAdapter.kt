package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotReactionEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.SpotReactionJpaRepository
import org.springframework.stereotype.Component

/**
 * 좋아요/싫어요 저장 포트 구현. 도메인↔엔티티 매핑을 여기서 담당한다(별도 Mapper 클래스 없음, 프로젝트 관례).
 *
 * PK는 surrogate id지만 upsert 단위는 자연키(member_id, content_id, language, UNIQUE). 재평가(upsert)는
 * 자연키로 관리 엔티티를 로드해 reaction만 갱신하고 dirty-checking으로 UPDATE를 낸다(관례). 없으면 새 엔티티 persist.
 * 트랜잭션은 호출자(서비스)가 연다.
 */
@Component
class SpotReactionPersistenceAdapter(
    private val repository: SpotReactionJpaRepository,
) : SpotReactionPort {

    override fun find(memberId: Long, contentId: String, language: DomainLocale): SpotReaction? =
        repository.findByMemberIdAndContentIdAndLanguage(memberId, contentId, language)?.toDomain()

    override fun save(reaction: SpotReaction) {
        val existing = repository.findByMemberIdAndContentIdAndLanguage(
            reaction.memberId, reaction.contentId, reaction.language,
        )
        if (existing != null) {
            // 관리 엔티티에 도메인 변경을 얹는다(dirty-checking → UPDATE). 자연키는 불변이라 reaction만 갱신.
            existing.reaction = reaction.reaction
        } else {
            repository.save(reaction.toNewEntity())
        }
    }

    private fun SpotReactionEntity.toDomain(): SpotReaction =
        SpotReaction.record(
            memberId = memberId,
            contentId = contentId,
            language = language,
            reaction = reaction,
        )

    private fun SpotReaction.toNewEntity(): SpotReactionEntity =
        SpotReactionEntity(
            memberId = memberId,
            contentId = contentId,
            language = language,
            reaction = reaction,
        )
}
