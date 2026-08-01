package org.around.ieuri.tourism.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.query.SpotReactionQueryPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotReactionEntity
import org.springframework.stereotype.Component

/**
 * 반응 기록 읽기 포트 구현(CQRS 읽기). QueryDSL로 content_id만 프로젝션한다(애그리거트 우회).
 */
@Component
class SpotReactionQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotReactionQueryPort {

    private val reaction = QSpotReactionEntity.spotReactionEntity

    override fun findReactedContentIds(
        memberId: Long,
        contentIds: Collection<String>,
        language: DomainLocale,
    ): Set<String> {
        if (contentIds.isEmpty()) return emptySet()
        return queryFactory
            .select(reaction.contentId)
            .from(reaction)
            .where(
                reaction.memberId.eq(memberId),
                reaction.language.eq(language),
                reaction.contentId.`in`(contentIds),
            )
            .fetch()
            .toSet()
    }

    override fun countLikes(contentId: String, language: DomainLocale): Long =
        queryFactory
            .select(reaction.count())
            .from(reaction)
            .where(
                reaction.contentId.eq(contentId),
                reaction.language.eq(language),
                reaction.reaction.eq(ReactionType.LIKE),
            )
            .fetchOne() ?: 0L
}
