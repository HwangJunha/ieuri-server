package org.around.ieuri.tourism.infrastructure.adapter.out.query

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.query.PrefetchTarget
import org.around.ieuri.tourism.application.port.out.query.SpotDetailPart
import org.around.ieuri.tourism.application.port.out.query.SpotDetailPrefetchTargetQueryPort
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotDetailEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotDetailImageEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotDetailInfoEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotDetailIntroEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 상세 사전 수집 대상 조회(SPEC-0037) — 그 갈래 자식 표에 행이 없거나 `fetched_at`이 지난 장소.
 * - `NOT EXISTS`다 — 조인하면 여러 행 자식 때문에 중복이 생기고 `limit`이 행 수를 센다.
 */
@Component
class SpotDetailPrefetchTargetQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotDetailPrefetchTargetQueryPort {

    private val spot = QSpotEntity.spotEntity
    private val detail = QSpotDetailEntity.spotDetailEntity
    private val image = QSpotDetailImageEntity.spotDetailImageEntity
    private val intro = QSpotDetailIntroEntity.spotDetailIntroEntity
    private val info = QSpotDetailInfoEntity.spotDetailInfoEntity

    @Transactional(readOnly = true)
    override fun findTargets(
        part: SpotDetailPart,
        afterSpotId: Long,
        limit: Int,
        staleBefore: Instant,
    ): List<PrefetchTarget> = queryFactory
        .select(
            spot.id,
            spot.contentId,
            spot.language,
            spot.category,
            spot.title,
            spot.thumbnailImageUrl,
            spot.copyright,
            spot.address,
        )
        .from(spot)
        .where(spot.id.gt(afterSpotId), needsFetch(part, staleBefore))
        .orderBy(spot.id.asc())
        .limit(limit.toLong())
        .fetch()
        .map { tuple ->
            PrefetchTarget(
                spotId = requireNotNull(tuple.get(spot.id)),
                contentId = requireNotNull(tuple.get(spot.contentId)),
                language = requireNotNull(tuple.get(spot.language)),
                category = SpotCategory(requireNotNull(tuple.get(spot.category))),
                title = requireNotNull(tuple.get(spot.title)),
                thumbnailImageUrl = requireNotNull(tuple.get(spot.thumbnailImageUrl)),
                copyright = requireNotNull(tuple.get(spot.copyright)),
                address = tuple.get(spot.address),
            )
        }

    /** 아직 확보하지 못했거나 신선도가 지난 장소. 확보 실패와 미확보를 구분하지 않는다. */
    private fun needsFetch(part: SpotDetailPart, staleBefore: Instant): BooleanExpression {
        val fresh = when (part) {
            SpotDetailPart.BODY ->
                JPAExpressions.selectOne().from(detail)
                    .where(detail.spotId.eq(spot.id), detail.fetchedAt.goe(staleBefore))

            SpotDetailPart.GALLERY ->
                JPAExpressions.selectOne().from(image)
                    .where(image.spotId.eq(spot.id), image.fetchedAt.goe(staleBefore))

            SpotDetailPart.INTRO ->
                JPAExpressions.selectOne().from(intro)
                    .where(intro.spotId.eq(spot.id), intro.fetchedAt.goe(staleBefore))

            SpotDetailPart.INFO ->
                JPAExpressions.selectOne().from(info)
                    .where(info.spotId.eq(spot.id), info.fetchedAt.goe(staleBefore))
        }
        return fresh.notExists()
    }
}
