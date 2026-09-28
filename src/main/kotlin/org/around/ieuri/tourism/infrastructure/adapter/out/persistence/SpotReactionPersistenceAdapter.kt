package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.SpotReactionJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 좋아요/싫어요 저장 포트 구현. 트랜잭션은 호출자(서비스)가 연다.
 * - 재평가는 자연키 `(member_id, catalog_id, language)`로 관리 엔티티를 로드해 dirty-checking으로 갱신하고, 없으면 persist한다.
 */
@Component
class SpotReactionPersistenceAdapter(
    private val repository: SpotReactionJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : SpotReactionPort {

    private val catalog = QSpotCatalogEntity.spotCatalogEntity

    override fun find(memberId: Long, catalogId: Long, language: DomainLocale): SpotReaction? = repository.findByMemberIdAndCatalogIdAndLanguage(memberId, catalogId, language)?.toDomain()

    override fun save(reaction: SpotReaction) {
        // 자연키 전체로 찾는다 — language가 빠지면 다른 언어의 행을 덮어쓴다.
        val existing = repository.findByMemberIdAndCatalogIdAndLanguage(
            reaction.memberId,
            reaction.catalogId,
            reaction.language,
        )
        if (existing != null) {
            // 키는 불변이라 reaction만 갱신한다(dirty-checking).
            existing.reaction = reaction.reaction
            return
        }
        repository.save(reaction.toNewEntity())
    }

    @Transactional(readOnly = true)
    override fun existsCatalog(catalogId: Long): Boolean = queryFactory
        .selectOne()
        .from(catalog)
        .where(catalog.id.eq(catalogId))
        .fetchFirst() != null

    /** 파생 삭제라 영속성 컨텍스트를 우회한다(같은 트랜잭션에서 다시 읽지 않는다). */
    override fun deleteByMember(memberId: Long): Long = repository.deleteByMemberId(memberId)
}
