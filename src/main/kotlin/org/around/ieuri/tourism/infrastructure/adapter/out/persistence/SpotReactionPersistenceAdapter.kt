package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.SpotReactionJpaRepository
import org.springframework.stereotype.Component

/**
 * 좋아요/싫어요 저장 포트 구현. 도메인↔엔티티 변환은 `mapper` 패키지가 담당한다.
 *
 * PK는 surrogate id지만 upsert 단위는 자연키 `(member_id, spot_id)`(UNIQUE). 재평가(upsert)는
 * 그 키로 관리 엔티티를 로드해 reaction만 갱신하고 dirty-checking으로 UPDATE를 낸다(관례).
 * 없으면 새 엔티티 persist. 트랜잭션은 호출자(서비스)가 연다.
 *
 * **V20 이후 `spot` 조회가 필요 없다.** 이전에는 자연키로 부모 id를 찾아야 했으나, 이제 호출자가
 * `spot.id`를 그대로 들고 온다 — 존재하지 않는 id는 FK가 거부한다.
 */
@Component
class SpotReactionPersistenceAdapter(
    private val repository: SpotReactionJpaRepository,
) : SpotReactionPort {

    override fun find(memberId: Long, spotId: Long): SpotReaction? =
        repository.findByMemberIdAndSpotId(memberId, spotId)?.toDomain()

    override fun save(reaction: SpotReaction) {
        val existing = repository.findByMemberIdAndSpotId(reaction.memberId, reaction.spotId)
        if (existing != null) {
            // 관리 엔티티에 도메인 변경을 얹는다(dirty-checking → UPDATE). 키는 불변이라 reaction만 갱신.
            existing.reaction = reaction.reaction
            return
        }
        repository.save(reaction.toNewEntity())
    }
}
