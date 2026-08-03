package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.exception.SpotNotFoundException
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotReactionEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.SpotJpaRepository
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
    private val spotRepository: SpotJpaRepository,
) : SpotReactionPort {

    override fun find(memberId: Long, contentId: String, language: DomainLocale): SpotReaction? =
        repository.findByMemberIdAndContentIdAndLanguage(memberId, contentId, language)?.toDomain()

    override fun save(reaction: SpotReaction) {
        val existing = repository.findByMemberIdAndContentIdAndLanguage(
            reaction.memberId, reaction.contentId, reaction.language,
        )
        if (existing != null) {
            // 관리 엔티티에 도메인 변경을 얹는다(dirty-checking → UPDATE). 자연키는 불변이라 reaction만 갱신.
            // spot_id도 자연키에 종속이라 불변이므로 건드리지 않는다 — 재조회 비용이 여기서는 들지 않는다.
            existing.reaction = reaction.reaction
            return
        }
        // 신규 생성 시에만 부모 id가 필요하다(V19). 존재하지 않는 관광지에는 반응할 수 없으므로
        // 부모 부재는 정상 흐름이 아니다 — 조용히 무시하면 사용자에게는 성공으로 보이면서 저장이
        // 안 되므로, 도메인 예외로 올려 서비스가 SPOT_NOT_FOUND로 표면화하게 한다.
        val spotId = spotRepository.findIdByContentIdAndLanguage(reaction.contentId, reaction.language)
            ?: throw SpotNotFoundException()
        repository.save(reaction.toNewEntity(spotId))
    }

    private fun SpotReactionEntity.toDomain(): SpotReaction =
        SpotReaction.record(
            memberId = memberId,
            contentId = contentId,
            language = language,
            reaction = reaction,
        )

    private fun SpotReaction.toNewEntity(spotId: Long): SpotReactionEntity =
        SpotReactionEntity(
            memberId = memberId,
            // 도메인에는 없는 값이다 — 부모 참조는 persistence 관심사이므로 매퍼 안에 가둔다(V19).
            spotId = spotId,
            contentId = contentId,
            language = language,
            reaction = reaction,
        )
}
