package org.around.ieuri.tourism.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotReactionEntity
import org.springframework.stereotype.Component

/**
 * 관광지 읽기 포트 구현(CQRS 읽기). QueryDSL로 필요한 값만 프로젝션한다(애그리거트 우회).
 * 현재 읽기 소스는 spot_reaction 한 테이블이다(반응 필터·좋아요 집계).
 */
@Component
class SpotQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotQueryPort {

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
