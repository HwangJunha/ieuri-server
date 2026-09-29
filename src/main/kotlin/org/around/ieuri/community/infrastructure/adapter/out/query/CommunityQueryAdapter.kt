package org.around.ieuri.community.infrastructure.adapter.out.query

import com.querydsl.core.types.Order
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.BooleanPath
import com.querydsl.core.types.dsl.DateTimePath
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.common.context.AppKey
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.community.application.port.out.query.BlockedUsersSearch
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.MyPostsSearch
import org.around.ieuri.community.application.port.out.query.PostCommentsSearch
import org.around.ieuri.community.application.port.out.query.PostsArea
import org.around.ieuri.community.application.port.out.query.PostsSearch
import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.around.ieuri.community.application.query.BlockedUserView
import org.around.ieuri.community.application.query.CommentView
import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.CommentEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QBlockEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QCommentEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostChipEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostHideEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostLikeEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostSpotEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostTagEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QSpotCatalogLanguageEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 커뮤니티 읽기 어댑터. QueryDSL이 read DTO로 직접 프로젝션한다.
 * - 모더레이션 필터는 [visibleToViewer] 하나를 모든 글 조회 경로가 공유한다.
 * - 필터는 조회 후가 아니라 쿼리 조건으로 건다(페이지 크기 보장).
 * - 작성자 닉네임 등 다른 도메인 소유 정보는 비워 두고 리졸버가 채운다.
 */
@Component
class CommunityQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : CommunityQueryPort {

    private val post = QPostEntity.postEntity
    private val comment = QCommentEntity.commentEntity
    private val photo = QPostPhotoEntity.postPhotoEntity
    private val like = QPostLikeEntity.postLikeEntity
    private val tag = QPostTagEntity.postTagEntity
    private val postChip = QPostChipEntity.postChipEntity
    private val postSpot = QPostSpotEntity.postSpotEntity
    private val catalogLanguage = QSpotCatalogLanguageEntity.spotCatalogLanguageEntity
    private val block = QBlockEntity.blockEntity
    private val postHide = QPostHideEntity.postHideEntity

    /** 최신순 피드. 반경 조건을 걸지 않는다(장소 없는 글 포함). 거리순은 [PostSpatialQueryAdapter]. */
    @Transactional(readOnly = true)
    override fun findPosts(search: PostsSearch): CursorPage<PostView> = findPosts(
        viewer = search.viewer,
        first = search.first,
        after = search.after,
        extraConditions = listOfNotNull(
            spotCondition(search.catalogId),
            languageCondition(search.language),
            categoryCondition(search.categories),
            tagCondition(search.normalizedTag),
            hideCondition(search),
        ),
    )

    /**
     * 활성 언어 필터(SPEC-0029). 붙은 장소가 이 언어에서 해소되지 않는 글을 뺀다(오류 아님).
     * - 장소가 없는 글은 남는다 — 앞 절(`NOT IN 연결`)을 빠뜨리면 전 언어에서 사라진다.
     */
    private fun languageCondition(language: ContentLanguage): BooleanExpression = post.id.notIn(JPAExpressions.select(postSpot.postId).from(postSpot))
        .or(
            post.id.`in`(
                JPAExpressions.select(postSpot.postId).from(postSpot)
                    .join(catalogLanguage)
                    .on(
                        catalogLanguage.catalogId.eq(postSpot.catalogId),
                        catalogLanguage.language.eq(language),
                    ),
            ),
        )

    /** 개별 숨김 필터. [PostsSearch.hiddenByViewer]가 true면 뒤집혀 숨긴 글만 남긴다. */
    private fun hideCondition(search: PostsSearch): BooleanExpression? {
        val hidden = search.viewer.hiddenPostIds
        return when {
            // 숨긴 글이 없는데 숨긴 글만 달라고 하면 결과도 없어야 한다.
            search.hiddenByViewer && hidden.isEmpty() -> post.id.isNull

            search.hiddenByViewer -> post.id.`in`(hidden)

            hidden.isEmpty() -> null

            else -> post.id.notIn(hidden)
        }
    }

    // 아래 셋은 조건이 필요 없으면 null을 돌려준다(QueryDSL where가 무시한다).

    /** 장소 필터. `spot_id`가 아니라 카탈로그로 건다 — 언어가 달라도 같은 장소의 글이 모인다(SPEC-0019). */
    private fun spotCondition(catalogId: Long?): BooleanExpression? = catalogId?.let {
        post.id.`in`(
            JPAExpressions.select(postSpot.postId).from(postSpot)
                .where(postSpot.catalogId.eq(it)),
        )
    }

    /** 다중 선택은 OR(IN)이다. 비어 있으면 조건을 걸지 않는다. */
    private fun categoryCondition(categories: Set<TargetCategory>): BooleanExpression? = categories.takeIf { it.isNotEmpty() }?.let {
        post.id.`in`(
            JPAExpressions.select(postChip.postId).from(postChip)
                .where(postChip.category.`in`(it)),
        )
    }

    private fun tagCondition(normalizedTag: String?): BooleanExpression? = normalizedTag?.let {
        post.id.`in`(
            JPAExpressions.select(tag.postId).from(tag)
                .where(tag.normalized.eq(it)),
        )
    }

    /** 단건 조회. 피드와 같은 [visibleToViewer]를 거친다 — 빼면 딥링크로 차단·숨김이 우회된다. */
    @Transactional(readOnly = true)
    override fun findPost(postId: Long, viewer: ViewerContext): PostView? {
        val entity = queryFactory
            .selectFrom(post)
            .where(post.id.eq(postId), visibleToViewer(viewer))
            .fetchOne() ?: return null

        return entity.toPostView(viewer)
    }

    @Transactional(readOnly = true)
    override fun findComments(search: PostCommentsSearch): CursorPage<CommentView> {
        val cursor = search.after?.let { decodeCursor(it) }
        val viewer = search.viewer

        val rows = queryFactory
            .selectFrom(comment)
            .where(
                comment.postId.eq(search.postId),
                comment.deletedAt.isNull,
                // 최상위 댓글만. 대댓글은 Comment.replies가 따로 내려준다.
                comment.parentCommentId.isNull,
                blockExclusion(viewer, comment.authorId),
                hiddenExclusion(viewer, comment.hidden, comment.authorId),
                cursor?.let { keysetCondition(comment.createdAt, comment.id, it) },
            )
            .orderBy(comment.createdAt.desc(), comment.id.desc())
            .limit((search.first + 1).toLong())
            .fetch()

        val hasNextPage = rows.size > search.first
        val pageRows = if (hasNextPage) rows.take(search.first) else rows
        val edges = pageRows.map { entity ->
            val createdAt = requireNotNull(entity.createdAt)
            val id = requireNotNull(entity.id)
            CursorEdge(cursor = encodeCursor(createdAt, id), node = entity.toCommentView(viewer))
        }
        return cursorPageOf(edges, hasNextPage = hasNextPage, hasPreviousPage = cursor != null)
    }

    /**
     * 여러 글의 댓글 미리보기(글당 최신 [limitPerPost]건)를 한 번에 읽는다.
     * - 주의: 대상 글의 댓글을 전부 읽고 메모리에서 자른다. 글당 댓글이 많아지면 바꿔야 한다.
     */
    @Transactional(readOnly = true)
    override fun findCommentPreviews(
        postIds: List<Long>,
        limitPerPost: Int,
        viewer: ViewerContext,
    ): Map<Long, List<CommentView>> {
        if (postIds.isEmpty()) return emptyMap()

        val rows = queryFactory
            .selectFrom(comment)
            .where(
                comment.postId.`in`(postIds),
                comment.deletedAt.isNull,
                blockExclusion(viewer, comment.authorId),
                hiddenExclusion(viewer, comment.hidden, comment.authorId),
            )
            .orderBy(comment.createdAt.desc(), comment.id.desc())
            .fetch()

        val previews = mutableMapOf<Long, MutableList<CommentView>>()
        for (entity in rows) {
            val bucket = previews.getOrPut(entity.postId) { mutableListOf() }
            if (bucket.size < limitPerPost) {
                bucket += entity.toCommentView(viewer)
            }
        }
        return previews
    }

    @Transactional(readOnly = true)
    override fun findLikeCount(postId: Long): Int? = queryFactory
        .select(post.likeCount)
        .from(post)
        .where(post.id.eq(postId))
        .fetchOne()

    /** 내가 쓴 글(SPEC-0011). [visibleToViewer]를 쓰지 않는다 — 숨김 글도 포함, 삭제된 글만 뺀다. */
    @Transactional(readOnly = true)
    override fun findMyPosts(search: MyPostsSearch): CursorPage<PostView> {
        val cursor = search.after?.let { decodeCursor(it) }
        val viewer = ViewerContext(memberId = search.memberId, blockedMemberIds = emptySet())

        val rows = queryFactory
            .selectFrom(post)
            .where(
                post.authorId.eq(search.memberId),
                post.deletedAt.isNull,
                cursor?.let { keysetCondition(post.createdAt, post.id, it) },
            )
            .orderBy(post.createdAt.desc(), post.id.desc())
            .limit((search.first + 1).toLong())
            .fetch()

        val hasNextPage = rows.size > search.first
        val pageRows = if (hasNextPage) rows.take(search.first) else rows
        val edges = pageRows.map { entity ->
            CursorEdge(
                cursor = encodeCursor(requireNotNull(entity.createdAt), requireNotNull(entity.id)),
                node = entity.toPostView(viewer),
            )
        }
        return cursorPageOf(edges, hasNextPage = hasNextPage, hasPreviousPage = cursor != null)
    }

    @Transactional(readOnly = true)
    override fun findBlockedUsers(search: BlockedUsersSearch): CursorPage<BlockedUserView> {
        val cursor = search.after?.let { decodeCursor(it) }

        val rows = queryFactory
            .selectFrom(block)
            .where(
                // 내가 건 차단만. 나를 차단한 사람은 뜨지 않는다.
                block.blockerId.eq(search.memberId),
                cursor?.let { keysetCondition(block.createdAt, block.id, it) },
            )
            .orderBy(block.createdAt.desc(), block.id.desc())
            .limit((search.first + 1).toLong())
            .fetch()

        val hasNextPage = rows.size > search.first
        val pageRows = if (hasNextPage) rows.take(search.first) else rows
        val edges = pageRows.map { entity ->
            val createdAt = requireNotNull(entity.createdAt)
            CursorEdge(
                cursor = encodeCursor(createdAt, requireNotNull(entity.id)),
                node = BlockedUserView(
                    member = PostAuthorView(id = entity.blockedId, nickname = "", profileImageUrl = null),
                    blockedAt = createdAt,
                ),
            )
        }
        return cursorPageOf(edges, hasNextPage = hasNextPage, hasPreviousPage = cursor != null)
    }

    /** 댓글 엔티티를 read DTO로. 작성자 표시 정보는 리졸버가 채운다. */
    private fun CommentEntity.toCommentView(viewer: ViewerContext) = CommentView(
        id = requireNotNull(id),
        parentCommentId = parentCommentId,
        body = body,
        author = PostAuthorView(id = authorId, nickname = "", profileImageUrl = null),
        ownedByViewer = authorId == viewer.memberId,
        hidden = hidden,
        createdAt = requireNotNull(createdAt),
    )

    /**
     * 여러 최상위 댓글의 대댓글을 한 번에 읽는다(DataLoader용).
     * - 대댓글은 오래된 순이며 페이지네이션이 없다.
     */
    @Transactional(readOnly = true)
    override fun findRepliesOf(
        parentCommentIds: List<Long>,
        viewer: ViewerContext,
    ): Map<Long, List<CommentView>> {
        if (parentCommentIds.isEmpty()) return emptyMap()

        return queryFactory
            .selectFrom(comment)
            .where(
                comment.parentCommentId.`in`(parentCommentIds),
                comment.deletedAt.isNull,
                blockExclusion(viewer, comment.authorId),
                hiddenExclusion(viewer, comment.hidden, comment.authorId),
            )
            .orderBy(comment.createdAt.asc(), comment.id.asc())
            .fetch()
            .groupBy(
                { requireNotNull(it.parentCommentId) },
                { it.toCommentView(viewer) },
            )
    }

    /** 양방향 차단 관계에서 상대 회원 id를 모은다. */
    @Transactional(readOnly = true)
    override fun findBlockedMemberIds(memberId: Long): Set<Long> = queryFactory
        .select(
            Expressions.cases()
                .`when`(block.blockerId.eq(memberId)).then(block.blockedId)
                .otherwise(block.blockerId),
        )
        .from(block)
        .where(block.blockerId.eq(memberId).or(block.blockedId.eq(memberId)))
        .fetch()
        .toSet()

    @Transactional(readOnly = true)
    override fun findHiddenPostIds(memberId: Long): Set<Long> = queryFactory
        .select(postHide.postId)
        .from(postHide)
        .where(postHide.memberId.eq(memberId))
        .fetch()
        .toSet()

    /** 피드 공통 조회. 경로별 조건만 [extraConditions]로 받는다. */
    private fun findPosts(
        viewer: ViewerContext,
        first: Int,
        after: String?,
        extraConditions: List<BooleanExpression>,
    ): CursorPage<PostView> {
        val cursor = after?.let { decodeCursor(it) }

        val rows = queryFactory
            .selectFrom(post)
            .where(
                *extraConditions.toTypedArray(),
                visibleToViewer(viewer),
                cursor?.let { keysetCondition(post.createdAt, post.id, it) },
            )
            .orderBy(post.createdAt.desc(), post.id.desc())
            .limit((first + 1).toLong())
            .fetch()

        val hasNextPage = rows.size > first
        val pageRows = if (hasNextPage) rows.take(first) else rows

        // 태그·사진·카테고리·장소·좋아요는 여기서 읽지 않는다(필드 선택 시 DataLoader).
        val edges = pageRows.map { entity ->
            CursorEdge(
                cursor = encodeCursor(
                    requireNotNull(entity.createdAt),
                    requireNotNull(entity.id),
                ),
                node = entity.toPostView(viewer),
            )
        }
        return cursorPageOf(edges, hasNextPage = hasNextPage, hasPreviousPage = cursor != null)
    }

    /**
     * 공유 모더레이션 필터.
     * - 삭제된 글은 누구에게도 보이지 않는다.
     * - 차단 관계(양방향)인 작성자를 뺀다. 미인증 호출자는 건너뛴다.
     * - hidden인 글을 빼되 작성자 본인에게는 남긴다.
     */
    private fun visibleToViewer(viewer: ViewerContext): BooleanExpression {
        var condition = post.deletedAt.isNull
        blockExclusion(viewer, post.authorId)?.let { condition = condition.and(it) }
        condition = condition.and(hiddenExclusion(viewer, post.hidden, post.authorId))
        return condition
    }

    /** 차단 제외. 차단 관계가 없으면 null(조건 없음). */
    private fun blockExclusion(viewer: ViewerContext, authorIdPath: NumberPath<Long>): BooleanExpression? {
        if (viewer.blockedMemberIds.isEmpty()) return null
        return authorIdPath.notIn(viewer.blockedMemberIds)
    }

    /** 숨김 제외. 숨겨진 글은 **작성자 본인에게만** 남는다. */
    private fun hiddenExclusion(
        viewer: ViewerContext,
        hiddenPath: BooleanPath,
        authorIdPath: NumberPath<Long>,
    ): BooleanExpression = hiddenPath.isFalse.or(authorIdPath.eq(viewer.memberId))

    /** (createdAt DESC, id DESC) keyset 조건. JPQL에 행 값 비교가 없어 OR로 편다. */
    private fun keysetCondition(
        createdAtPath: DateTimePath<Instant>,
        idPath: NumberPath<Long>,
        cursor: CommunityCursor,
    ): BooleanExpression = createdAtPath.lt(cursor.createdAt)
        .or(createdAtPath.eq(cursor.createdAt).and(idPath.lt(cursor.id)))

    @Transactional(readOnly = true)
    override fun findCategoriesOf(postIds: List<Long>): Map<Long, TargetCategory> {
        if (postIds.isEmpty()) return emptyMap()

        return queryFactory
            .select(postChip.postId, postChip.category)
            .from(postChip)
            .where(postChip.postId.`in`(postIds))
            .fetch()
            .associate {
                requireNotNull(it.get(postChip.postId)) to requireNotNull(it.get(postChip.category))
            }
    }

    @Transactional(readOnly = true)
    override fun findCatalogIdsOf(postIds: List<Long>): Map<Long, Long> {
        if (postIds.isEmpty()) return emptyMap()

        return queryFactory
            .select(postSpot.postId, postSpot.catalogId)
            .from(postSpot)
            .where(postSpot.postId.`in`(postIds))
            .fetch()
            .associate {
                requireNotNull(it.get(postSpot.postId)) to requireNotNull(it.get(postSpot.catalogId))
            }
    }

    /** 글별로 묶어 돌려준다. 태그가 없는 글은 키가 없다. */
    @Transactional(readOnly = true)
    override fun findTagsOf(postIds: List<Long>): Map<Long, List<String>> {
        if (postIds.isEmpty()) return emptyMap()

        val rows = queryFactory
            .select(tag.postId, tag.name)
            .from(tag)
            .where(tag.postId.`in`(postIds))
            .orderBy(tag.id.asc())
            .fetch()

        val grouped = mutableMapOf<Long, MutableList<String>>()
        for (row in rows) {
            val postId = requireNotNull(row.get(tag.postId))
            grouped.getOrPut(postId) { mutableListOf() } += requireNotNull(row.get(tag.name))
        }
        return grouped
    }

    /** position 순서가 표시 순서다. */
    @Transactional(readOnly = true)
    override fun findPhotoKeysOf(postIds: List<Long>): Map<Long, List<String>> {
        if (postIds.isEmpty()) return emptyMap()

        val rows = queryFactory
            .select(photo.postId, photo.objectKey)
            .from(photo)
            .where(photo.postId.`in`(postIds))
            .orderBy(photo.position.asc())
            .fetch()

        val grouped = mutableMapOf<Long, MutableList<String>>()
        for (row in rows) {
            val postId = requireNotNull(row.get(photo.postId))
            grouped.getOrPut(postId) { mutableListOf() } += requireNotNull(row.get(photo.objectKey))
        }
        return grouped
    }

    @Transactional(readOnly = true)
    override fun findLikedPostIds(memberId: Long, postIds: List<Long>): Set<Long> {
        if (postIds.isEmpty()) return emptySet()
        return queryFactory
            .select(like.postId)
            .from(like)
            .where(like.memberId.eq(memberId), like.postId.`in`(postIds))
            .fetch()
            .toSet()
    }
}
