package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.SpotReactionJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 좋아요/싫어요 저장 포트 구현. 도메인↔엔티티 변환은 `mapper` 패키지가 담당한다.
 *
 * PK는 surrogate id지만 upsert 단위는 자연키 `(member_id, catalog_id)`(UNIQUE, SPEC-0019).
 * 재평가(upsert)는 그 키로 관리 엔티티를 로드해 reaction만 갱신하고 dirty-checking으로 UPDATE를
 * 낸다(관례). 없으면 새 엔티티 persist. 트랜잭션은 호출자(서비스)가 연다.
 */
@Component
class SpotReactionPersistenceAdapter(
    private val repository: SpotReactionJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : SpotReactionPort {

    private val spot = QSpotEntity.spotEntity

    override fun find(memberId: Long, catalogId: Long): SpotReaction? =
        repository.findByMemberIdAndCatalogId(memberId, catalogId)?.toDomain()

    override fun save(reaction: SpotReaction) {
        val existing = repository.findByMemberIdAndCatalogId(reaction.memberId, reaction.catalogId)
        if (existing != null) {
            // 관리 엔티티에 도메인 변경을 얹는다(dirty-checking → UPDATE). 키는 불변이라 reaction만 갱신.
            //
            // **spotId는 갱신한다** — 재평가한 화면의 언어를 최신으로 남긴다. 식별의 일부가
            // 아니므로 바뀌어도 키가 흔들리지 않는다.
            existing.reaction = reaction.reaction
            existing.spotId = reaction.spotId
            return
        }
        repository.save(reaction.toNewEntity())
    }

    /**
     * `spot.id` → 소속 카탈로그. 배정되지 않은 행은 `catalog_id`가 null이라 결과도 null이다 —
     * 호출자가 그것을 `SPOT_NOT_FOUND`로 해석한다.
     */
    @Transactional(readOnly = true)
    override fun findCatalogIdBySpotId(spotId: Long): Long? =
        queryFactory
            .select(spot.catalogId)
            .from(spot)
            .where(spot.id.eq(spotId))
            .fetchFirst()

    /**
     * 파생 삭제라 영속성 컨텍스트를 우회한다. 이 트랜잭션에서 같은 엔티티를 다시 읽는 코드가
     * 없으므로 안전하다. 트랜잭션은 호출자(서비스)가 연다.
     */
    override fun deleteByMember(memberId: Long): Long = repository.deleteByMemberId(memberId)
}
