package org.around.ieuri.community.infrastructure.adapter.out.query

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.DateTimePath
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.community.application.port.out.query.FeedQueryPort
import org.around.ieuri.community.application.port.out.query.FeedSearch
import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.around.ieuri.community.application.query.FeedItemView
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.FeedItemEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QFeedItemEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QFeedItemLikeEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QFeedItemPhotoEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 피드 읽기 어댑터(SPEC-0017). QueryDSL이 read DTO로 직접 프로젝션하며 애그리게이트를 우회한다.
 *
 * **차단 필터를 쿼리 조건으로 건다**(AC11-1·11-2). 조회 후 걸러내면 `first: 20`을 요청했는데
 * 15건만 남고 hasNextPage가 거짓말을 한다.
 *
 * spot의 제목·썸네일과 작성자 닉네임은 다른 도메인 소유라 여기서 조인하지 않는다 —
 * 리졸버가 필드 단위로 조회한다.
 */
@Component
class FeedQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : FeedQueryPort {

    private val feedItem = QFeedItemEntity.feedItemEntity
    private val photo = QFeedItemPhotoEntity.feedItemPhotoEntity
    private val like = QFeedItemLikeEntity.feedItemLikeEntity

    /**
     * 피드 한 페이지. **정렬은 (createdAt DESC, id DESC) 고정이다** — 정렬 인자가 없으므로
     * 커서 형식이 정렬 키에 종속될 걱정이 없다.
     */
    @Transactional(readOnly = true)
    override fun findFeed(search: FeedSearch): CursorPage<FeedItemView> {
        val cursor = search.after?.let { decodeCursor(it) }
        val viewer = search.viewer

        val rows = queryFactory
            .selectFrom(feedItem)
            .where(
                visibleToViewer(viewer),
                cursor?.let { keysetCondition(feedItem.createdAt, feedItem.id, it) },
            )
            .orderBy(feedItem.createdAt.desc(), feedItem.id.desc())
            // 초과분 한 건의 존재가 곧 다음 페이지의 존재다 — 그것 하나 알자고 카운트 쿼리를 돌지 않는다.
            .limit((search.first + 1).toLong())
            .fetch()

        val hasNextPage = rows.size > search.first
        val pageRows = if (hasNextPage) rows.take(search.first) else rows
        val edges = pageRows.map { entity ->
            val createdAt = requireNotNull(entity.createdAt)
            val id = requireNotNull(entity.id)
            CursorEdge(cursor = encodeCursor(createdAt, id), node = entity.toFeedItemView(viewer))
        }
        return cursorPageOf(edges, hasNextPage = hasNextPage, hasPreviousPage = cursor != null)
    }

    /** 단건도 피드와 **같은 필터를 거친다** — 딥링크가 차단을 우회하는 통로가 되지 않게 한다. */
    @Transactional(readOnly = true)
    override fun findFeedItem(feedItemId: Long, viewer: ViewerContext): FeedItemView? =
        queryFactory
            .selectFrom(feedItem)
            .where(feedItem.id.eq(feedItemId), visibleToViewer(viewer))
            .fetchOne()
            ?.toFeedItemView(viewer)

    @Transactional(readOnly = true)
    override fun findPhotoKeysOf(feedItemIds: List<Long>): Map<Long, List<String>> {
        if (feedItemIds.isEmpty()) return emptyMap()

        return queryFactory
            .select(photo.feedItemId, photo.objectKey)
            .from(photo)
            .where(photo.feedItemId.`in`(feedItemIds))
            .orderBy(photo.feedItemId.asc(), photo.position.asc())
            .fetch()
            .groupBy({ it.get(photo.feedItemId)!! }, { it.get(photo.objectKey)!! })
    }

    @Transactional(readOnly = true)
    override fun findLikedFeedItemIds(memberId: Long, feedItemIds: List<Long>): Set<Long> {
        if (feedItemIds.isEmpty()) return emptySet()

        return queryFactory
            .select(like.feedItemId)
            .from(like)
            .where(like.memberId.eq(memberId), like.feedItemId.`in`(feedItemIds))
            .fetch()
            .toSet()
    }

    @Transactional(readOnly = true)
    override fun findLikeCount(feedItemId: Long): Int? =
        queryFactory
            .select(feedItem.likeCount)
            .from(feedItem)
            .where(feedItem.id.eq(feedItemId))
            .fetchOne()

    /**
     * 모더레이션 필터. 피드와 단건이 **이 하나를 공유한다** — 경로마다 다시 쓰면 나중에
     * 하나만 고쳐지는 사고가 난다.
     */
    private fun visibleToViewer(viewer: ViewerContext): BooleanExpression {
        val base = feedItem.deletedAt.isNull
            .and(hiddenExclusion(viewer))
        val blocked = blockExclusion(viewer) ?: return base
        return base.and(blocked)
    }

    /** 차단 제외. **양방향으로 해석된 집합**이 들어온다 — 내가 차단한 쪽과 나를 차단한 쪽이다. */
    private fun blockExclusion(viewer: ViewerContext): BooleanExpression? {
        if (viewer.blockedMemberIds.isEmpty()) return null
        return feedItem.authorId.notIn(viewer.blockedMemberIds)
    }

    /** 신고 누적으로 숨겨진 피드는 **작성자 본인에게만** 남는다. */
    private fun hiddenExclusion(viewer: ViewerContext): BooleanExpression =
        feedItem.hidden.isFalse.or(feedItem.authorId.eq(viewer.memberId))

    /**
     * keyset 조건. 정렬이 (createdAt DESC, id DESC)이므로 다음 페이지는 그 튜플보다 "작은" 행이다.
     *
     * JPQL에는 행 값 생성자가 없어 `(a, b) < (?, ?)`를 `a < ? OR (a = ? AND b < ?)`로 편다.
     */
    private fun keysetCondition(
        createdAtPath: DateTimePath<Instant>,
        idPath: NumberPath<Long>,
        cursor: CommunityCursor,
    ): BooleanExpression =
        createdAtPath.lt(cursor.createdAt)
            .or(createdAtPath.eq(cursor.createdAt).and(idPath.lt(cursor.id)))

    /**
     * 사진·좋아요 여부는 **여기서 채우지 않는다** — 클라이언트가 그 필드를 고른 경우에만
     * DataLoader가 한 번에 읽는다. 미리 채우면 `{ id body }`만 물어도 조회가 더 붙는다.
     */
    private fun FeedItemEntity.toFeedItemView(viewer: ViewerContext) = FeedItemView(
        id = requireNotNull(id) { "조회된 피드에는 id가 있어야 한다." },
        body = body,
        spotId = spotId,
        authorId = authorId,
        likeCount = likeCount,
        ownedByViewer = authorId == viewer.memberId,
        createdAt = requireNotNull(createdAt) { "조회된 피드에는 createdAt이 있어야 한다." },
    )
}
