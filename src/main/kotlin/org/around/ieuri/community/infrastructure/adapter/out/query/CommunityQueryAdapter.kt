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
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.community.application.port.out.query.BlockedUsersSearch
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
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
import org.around.ieuri.community.domain.vo.AppKey
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.PostSortOrder
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.CommentEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QBlockEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QCommentEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QCommunityChipEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostLikeEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostChipEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostSpotEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostTagEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 커뮤니티 읽기 어댑터. QueryDSL이 read DTO로 직접 프로젝션하며 애그리게이트를 우회한다.
 *
 * **모더레이션 필터가 이 클래스에 하나만 있다** — [visibleToViewer]를 피드 3종과 단건 조회가
 * 모두 공유한다. 경로마다 조건을 다시 쓰면 나중에 하나만 고쳐지는 사고가 난다.
 *
 * 필터는 조회 후가 아니라 **쿼리 조건으로** 건다. 조회 후 걸러내면 `first: 20`을 요청했는데
 * 15건만 남는다.
 *
 * 대상의 제목·썸네일과 작성자 닉네임은 다른 도메인 소유라 여기서 조인하지 않는다 —
 * 리졸버가 필드 단위로 조회한다.
 */
@Component
class CommunityQueryAdapter(
    private val queryFactory: JPAQueryFactory,
    private val spatialQueryAdapter: PostSpatialQueryAdapter,
) : CommunityQueryPort {

    private val post = QPostEntity.postEntity
    private val comment = QCommentEntity.commentEntity
    private val photo = QPostPhotoEntity.postPhotoEntity
    private val like = QPostLikeEntity.postLikeEntity
    private val chip = QCommunityChipEntity.communityChipEntity
    private val tag = QPostTagEntity.postTagEntity
    private val postChip = QPostChipEntity.postChipEntity
    private val postSpot = QPostSpotEntity.postSpotEntity
    private val block = QBlockEntity.blockEntity


    /**
     * 글 피드. 대상 범위만 다르고 정렬·페이징·모더레이션 필터는 세 경우가 같다.
     *
     * [PostsSearch.targets]가 null이면 대상으로 좁히지 않고, 빈 목록이면 결과도 비어 있다 —
     * 빈 `IN` 절은 SQL에서 유효하지 않으므로 조기 반환한다.
     */
    @Transactional(readOnly = true)
    override fun findPosts(search: PostsSearch): CursorPage<PostView> =
        when (search.sort) {
            PostSortOrder.LATEST -> findPosts(
                viewer = search.viewer,
                first = search.first,
                after = search.after,
                extraConditions = listOfNotNull(
                    spotCondition(search.spotId),
                    // **반경은 LATEST에서 걸지 않는다** — 장소 없이 쓴 글도 함께 담기 위함이다.
                    // 그러지 않으면 그런 글이 지역 피드에서 영원히 보이지 않는다.
                    categoryCondition(search.category),
                    tagCondition(search.normalizedTag),
                ),
            )

            PostSortOrder.DISTANCE -> findPostsByDistance(search)
        }

    /**
     * 거리순 피드. 반경·거리 정렬은 [PostSpatialQueryAdapter]가 생 SQL로 처리하고
     (PostGIS 함수는 QueryDSL로 표현되지 않는다), 여기서는 그 id로 글을 읽어 나머지 필터를 건다.
     *
     * **두 단계로 나눈 이유**: geography 컬럼을 엔티티에 매핑할 수 없어(대응 Kotlin 타입이
     * 없고, 매핑하면 ddl-auto=validate가 기동을 막는다) 반경 조건을 QueryDSL로 쓸 방법이 없다.
     * map_x/map_y로 매번 점을 만들면 표현은 되지만 GiST 인덱스를 타지 못한다
     * (실측: Seq Scan 389ms 대 Bitmap Index Scan 119ms).
     *
     * 공간 조회는 **차단·숨김을 모른다.** 그래서 넉넉히 읽고 여기서 다시 좁힌 뒤 잘라낸다 —
     * 필터를 양쪽에 두면 한쪽만 고쳐지는 사고가 난다.
     */
    private fun findPostsByDistance(search: PostsSearch): CursorPage<PostView> {
        val area = requireNotNull(search.area) { "거리순은 좌표를 준 경우에만 온다." }
        val cursor = search.after?.let { decodeDistanceCursor(it) }

        val spatialRows = spatialQueryAdapter.findPostIdsWithin(
            area = area,
            // 차단·숨김으로 걸러질 몫을 감안해 넉넉히 읽는다. 그래도 모자라면 그 페이지가
            // 짧아질 뿐이며 hasNextPage가 false가 되어 조회가 끝난다.
            limit = (search.first + 1) * SPATIAL_OVERFETCH,
            afterDistanceMeters = cursor?.distanceMeters,
            afterCreatedAtEpochMilli = cursor?.createdAt?.toEpochMilli(),
            afterId = cursor?.id,
        )
        if (spatialRows.isEmpty()) {
            return cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = cursor != null)
        }

        val distanceByPostId = spatialRows.associate { it.postId to it.distanceMeters }
        val visible = queryFactory
            .selectFrom(post)
            .where(
                post.id.`in`(distanceByPostId.keys),
                spotCondition(search.spotId),
                categoryCondition(search.category),
                tagCondition(search.normalizedTag),
                visibleToViewer(search.viewer),
            )
            .fetch()
            // 공간 조회가 정한 순서를 그대로 따른다 — 거리·최신·id 순이 이미 반영돼 있다.
            .associateBy { requireNotNull(it.id) }

        val ordered = spatialRows.mapNotNull { visible[it.postId] }
        val hasNextPage = ordered.size > search.first
        val edges = (if (hasNextPage) ordered.take(search.first) else ordered).map { entity ->
            val postId = requireNotNull(entity.id)
            CursorEdge(
                cursor = encodeDistanceCursor(
                    distanceMeters = requireNotNull(distanceByPostId[postId]),
                    createdAt = requireNotNull(entity.createdAt),
                    id = postId,
                ),
                node = entity.toPostView(search.viewer),
            )
        }
        return cursorPageOf(edges, hasNextPage = hasNextPage, hasPreviousPage = cursor != null)
    }

    /**
     * 아래 셋은 **조건이 필요 없으면 null**을 돌려준다. QueryDSL의 where는 null을 무시하므로
     * 호출부에서 if로 리스트를 쌓지 않아도 된다 — 이 파일의 다른 필터와 같은 관용구다.
     */
    private fun spotCondition(spotId: Long?): BooleanExpression? =
        spotId?.let {
            // 연결 테이블을 서브쿼리로 걸어 조인이 행을 늘리지 않게 한다.
            post.id.`in`(
                JPAExpressions.select(postSpot.postId).from(postSpot)
                    .where(postSpot.spotId.eq(it)),
            )
        }

    private fun categoryCondition(category: TargetCategory?): BooleanExpression? =
        category?.let {
            post.id.`in`(
                JPAExpressions.select(postChip.postId).from(postChip)
                    .where(postChip.category.eq(it)),
            )
        }

    private fun tagCondition(normalizedTag: String?): BooleanExpression? =
        normalizedTag?.let {
            post.id.`in`(
                JPAExpressions.select(tag.postId).from(tag)
                    .where(tag.normalized.eq(it)),
            )
        }

    /**
     * 단건 조회. **피드와 같은 필터를 거친다** — 여기가 뚫리면 딥링크 하나로 차단과 자동 숨김이
     * 통째로 우회되므로, 조건을 따로 쓰지 않고 [visibleToViewer]를 그대로 쓴다.
     */
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
                // **최상위 댓글만.** 대댓글은 Comment.replies가 부모별로 따로 내려준다 —
                // 섞어서 평평하게 주면 클라이언트가 트리를 다시 조립해야 한다.
                comment.parentCommentId.isNull,
                // 피드와 같은 두 단계: 차단 제외 후 숨김 제외(작성자 본인은 예외).
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
     * 여러 글의 댓글 미리보기를 한 번에. **글 하나당 최신 몇 건**만 필요하므로 전부 읽어
     * 잘라내지 않고, 글마다 상한을 걸어 읽는다.
     *
     * QueryDSL은 윈도 함수를 쓸 수 있으나 JPQL로는 `ROW_NUMBER() OVER (PARTITION BY ...)`를
     * 표현할 수 없다. 대신 **댓글이 적다는 사실**에 기대 목록 전체를 읽고 메모리에서 자른다 —
     * 글당 댓글이 수백 건씩 쌓이면 이 방식은 바꿔야 하며, 그때는 전용 raw SQL 어댑터로 뺀다.
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

    /** 커맨드가 원자적으로 갱신한 뒤 읽는다 — 쓰기가 애그리게이트를 거치지 않기 때문이다. */
    @Transactional(readOnly = true)
    override fun findLikeCount(postId: Long): Int? =
        queryFactory
            .select(post.likeCount)
            .from(post)
            .where(post.id.eq(postId))
            .fetchOne()

    @Transactional(readOnly = true)
    override fun findBlockedUsers(search: BlockedUsersSearch): CursorPage<BlockedUserView> {
        val cursor = search.after?.let { decodeCursor(it) }

        val rows = queryFactory
            .selectFrom(block)
            .where(
                // 내가 건 차단만. 나를 차단한 사람은 이 목록에 뜨지 않는다 — 해제 UI를 위한
                // 목록이고, 남이 건 차단은 내가 풀 수 없다.
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

    /**
     * 댓글 엔티티를 read DTO로. 작성자 표시 정보는 다른 도메인 소유라 리졸버가 채운다.
     * 목록과 미리보기가 같은 모양이어야 하므로 한 곳에 둔다.
     */
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
     * 여러 최상위 댓글의 대댓글을 **한 번에** 읽는다. 댓글마다 물으면 목록 한 페이지에
     * 쿼리가 그 수만큼 붙는다(N+1) — DataLoader가 부모 id를 모아 이 메서드를 한 번 부른다.
     *
     * 대댓글은 **오래된 순**이다. 대화의 흐름이라 최신순으로 뒤집으면 문맥이 끊긴다
     * (최상위 목록은 여전히 최신순이다). 페이지네이션은 없다 — 목록 안의 목록에 커서를 두면
     * 화면마다 커서 관리가 갈린다.
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

    /**
     * 차단 관계를 **한 번의 쿼리로** 읽는다. 양방향이라 두 방향을 OR로 묶고, 내가 어느 쪽이냐에
     * 따라 상대 id가 달라지므로 CASE로 고른다.
     *
     * 엔티티를 로드하지 않고 id만 프로젝션한다 — 필요한 것은 상대 id 하나뿐이다.
     */
    @Transactional(readOnly = true)
    override fun findBlockedMemberIds(memberId: Long): Set<Long> =
        queryFactory
            .select(
                Expressions.cases()
                    .`when`(block.blockerId.eq(memberId)).then(block.blockedId)
                    .otherwise(block.blockerId),
            )
            .from(block)
            .where(block.blockerId.eq(memberId).or(block.blockedId.eq(memberId)))
            .fetch()
            .toSet()

    /** 앱의 켜진 칩만 순서대로. 구성이 없으면 빈 목록이다 — 오류가 아니다. */
    @Transactional(readOnly = true)
    override fun findChips(appKey: AppKey, language: ContentLanguage): List<CommunityChipView> =
        queryFactory
            .select(chip.category, chip.position)
            .from(chip)
            .where(chip.appKey.eq(appKey.value), chip.language.eq(language), chip.enabled.isTrue)
            .orderBy(chip.position.asc(), chip.id.asc())
            .fetch()
            .map {
                CommunityChipView(
                    category = requireNotNull(it.get(chip.category)),
                    position = requireNotNull(it.get(chip.position)).toInt(),
                )
            }

    /**
     * 피드 공통 조회. spotPosts·nearbyPosts가 조건만 달리해 이 하나를 쓴다 —
     * 정렬·페이징·모더레이션 필터가 두 경로에서 갈리지 않게 하려는 것이다.
     */
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

        // 태그·사진·카테고리·장소·좋아요는 **여기서 읽지 않는다.** 클라이언트가 그 필드를
        // 골랐을 때만 DataLoader가 페이지 전체를 한 번에 읽는다.
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
     * **네 조회 경로가 공유하는 모더레이션 필터.** 두 단계다.
     *  1. 차단 제외 — 호출자와 차단 관계(양방향)에 있는 작성자를 제외한다. 미인증 호출자는
     *     차단 관계가 없으므로 이 단계를 건너뛴다.
     *  2. 숨김 제외 — hidden인 글을 제외하되 작성자 본인에게는 남긴다.
     *
     * 삭제된 글은 누구에게도 보이지 않는다.
     */
    private fun visibleToViewer(viewer: ViewerContext): BooleanExpression {
        var condition = post.deletedAt.isNull
        blockExclusion(viewer, post.authorId)?.let { condition = condition.and(it) }
        hiddenExclusion(viewer, post.hidden, post.authorId)?.let { condition = condition.and(it) }
        return condition
    }

    /**
     * 차단 제외. 차단 관계가 없으면 null을 돌려주고, QueryDSL은 null 조건을 그대로 버린다.
     */
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
    override fun findSpotIdsOf(postIds: List<Long>): Map<Long, Long> {
        if (postIds.isEmpty()) return emptyMap()

        return queryFactory
            .select(postSpot.postId, postSpot.spotId)
            .from(postSpot)
            .where(postSpot.postId.`in`(postIds))
            .fetch()
            .associate {
                requireNotNull(it.get(postSpot.postId)) to requireNotNull(it.get(postSpot.spotId))
            }
    }

    /** 입력 순서와 무관하게 글별로 묶어 돌려준다. 태그가 없는 글은 키가 없다. */
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

    /** community_post 한 행이 가진 것만 옮긴다. 나머지 필드는 DataLoader가 채운다. */
    private fun PostEntity.toPostView(viewer: ViewerContext): PostView =
        PostView(
            id = requireNotNull(id),
            title = title,
            body = body,
            authorId = authorId,
            likeCount = likeCount,
            commentCount = commentCount,
            viewCount = viewCount,
            ownedByViewer = authorId == viewer.memberId,
            hidden = hidden,
            edited = edited,
            createdAt = requireNotNull(createdAt),
        )

    private companion object {
        /**
         * 공간 조회가 차단·숨김을 모르므로 그만큼 더 읽는다. 3배는 실측이 아니라 여유값이며,
         * 모자라면 그 페이지가 짧아질 뿐 정합성이 깨지지는 않는다.
         */
        const val SPATIAL_OVERFETCH = 3
    }
}
