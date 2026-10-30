package org.around.ieuri.community.adapter.persistence.query

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.persistence.geographyPoint
import org.around.ieuri.common.persistence.stDWithin
import org.around.ieuri.common.persistence.stDistance
import org.around.ieuri.community.adapter.persistence.mapper.toPostView
import org.around.ieuri.community.adapter.persistence.table.PostChipTable
import org.around.ieuri.community.adapter.persistence.table.PostHideTable
import org.around.ieuri.community.adapter.persistence.table.PostLikeTable
import org.around.ieuri.community.adapter.persistence.table.PostPhotoTable
import org.around.ieuri.community.adapter.persistence.table.PostSpotTable
import org.around.ieuri.community.adapter.persistence.table.PostTable
import org.around.ieuri.community.adapter.persistence.table.PostTagTable
import org.around.ieuri.community.adapter.persistence.table.SpotCatalogLanguageTable
import org.around.ieuri.community.application.post.provided.dto.PostView
import org.around.ieuri.community.application.post.required.PostQueryPort
import org.around.ieuri.community.application.post.required.dto.MyPostsSearch
import org.around.ieuri.community.application.post.required.dto.PostsSearch
import org.around.ieuri.community.application.shared.required.dto.ViewerContext
import org.around.ieuri.community.domain.post.TargetCategory
import org.around.ieuri.community.domain.shared.ContentLanguage
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

/**
 * 글 읽기 어댑터. read DTO로 직접 프로젝션한다. 트랜잭션은 호출하는 조회 서비스가 연다.
 * - 모더레이션 필터는 [visibleToViewer] 하나를 모든 글 조회 경로가 공유한다. 뒤에서 거르면 페이지 크기가 깨진다.
 */
@Component
class PostQueryAdapter : PostQueryPort {

    /** 최신순 피드. 반경 조건을 걸지 않는다(장소 없는 글 포함). */
    override fun findPosts(search: PostsSearch): CursorPage<PostView> = postPage(
        viewer = search.viewer,
        first = search.first,
        after = search.after,
        extra = allOf(
            search.catalogId?.let(::spotCondition),
            languageCondition(search.language),
            search.categories.takeIf { it.isNotEmpty() }?.let(::categoryCondition),
            search.normalizedTag?.let(::tagCondition),
            hideCondition(search),
        ),
    )

    /**
     * 거리순 피드. 정렬은 (거리 ASC, created_at DESC, id DESC)이고 커서 비교도 그 순서다.
     * - 장소 없는 글은 오지 않는다(`community_post_spot` INNER JOIN). 장소 필터는 카탈로그로 건다.
     */
    override fun findPostsByDistance(search: PostsSearch): CursorPage<PostView> {
        val area = requireNotNull(search.area) { "거리순은 좌표를 준 경우에만 온다." }
        val cursor = search.after?.let(::decodeDistanceCursor)
        // `geographyPoint`는 (경도, 위도) 순서다 — 뒤집으면 조용히 틀린다.
        val center = geographyPoint(area.longitude, area.latitude)
        val distance = stDistance(PostSpotTable.location, center)
        val rows = PostTable.join(PostSpotTable, JoinType.INNER, PostTable.id, PostSpotTable.postId)
            .select(PostTable.columns + distance)
            .where {
                allOf(
                    stDWithin(PostSpotTable.location, center, area.radiusMeters.toDouble()),
                    visibleToViewer(search.viewer),
                    hideCondition(search),
                    search.catalogId?.let { PostSpotTable.catalogId eq it },
                    PostSpotTable.catalogId inSubQuery SpotCatalogLanguageTable.select(SpotCatalogLanguageTable.catalogId).where { SpotCatalogLanguageTable.language eq search.language.name },
                    search.categories.takeIf { it.isNotEmpty() }?.let(::categoryCondition),
                    search.normalizedTag?.let(::tagCondition),
                    cursor?.let { (distance greater it.distanceMeters) or ((distance eq it.distanceMeters) and keysetBefore(PostTable.createdAt, PostTable.id, CommunityCursor(it.createdAt, it.id))) },
                )
            }
            .orderBy(distance to SortOrder.ASC, PostTable.createdAt to SortOrder.DESC, PostTable.id to SortOrder.DESC)
            .limit(search.first + 1)
            .toList()
        return page(rows, search.first, cursor != null) { row ->
            CursorEdge(encodeDistanceCursor(row[distance], row[PostTable.createdAt].toInstant(), row[PostTable.id]), row.toPostView(search.viewer.memberId))
        }
    }

    /** 단건. 피드와 같은 [visibleToViewer]를 거친다 — 빼면 딥링크로 차단·숨김이 우회된다. */
    override fun findPost(postId: Long, viewer: ViewerContext): PostView? = PostTable.selectAll()
        .where { (PostTable.id eq postId) and visibleToViewer(viewer) }
        .singleOrNull()?.toPostView(viewer.memberId)

    /** 내가 쓴 글. [visibleToViewer]를 쓰지 않는다 — 숨김 글도 포함, 삭제된 글만 뺀다. */
    override fun findMyPosts(search: MyPostsSearch): CursorPage<PostView> {
        val cursor = search.after?.let(::decodeCursor)
        val rows = PostTable.selectAll()
            .where {
                allOf(
                    PostTable.authorId eq search.memberId,
                    PostTable.deletedAt.isNull(),
                    cursor?.let { keysetBefore(PostTable.createdAt, PostTable.id, it) },
                )
            }
            .orderBy(PostTable.createdAt to SortOrder.DESC, PostTable.id to SortOrder.DESC)
            .limit(search.first + 1)
            .toList()
        return page(rows, search.first, cursor != null) { row ->
            CursorEdge(encodeCursor(row[PostTable.createdAt].toInstant(), row[PostTable.id]), row.toPostView(search.memberId))
        }
    }

    override fun findHiddenPostIds(memberId: Long): Set<Long> = PostHideTable.select(PostHideTable.postId)
        .where { PostHideTable.memberId eq memberId }
        .map { it[PostHideTable.postId] }
        .toSet()

    override fun findCategoriesOf(postIds: List<Long>): Map<Long, TargetCategory> {
        if (postIds.isEmpty()) return emptyMap()
        return PostChipTable.select(PostChipTable.postId, PostChipTable.category)
            .where { PostChipTable.postId inList postIds }
            .associate { it[PostChipTable.postId] to TargetCategory.valueOf(it[PostChipTable.category]) }
    }

    override fun findCatalogIdsOf(postIds: List<Long>): Map<Long, Long> {
        if (postIds.isEmpty()) return emptyMap()
        return PostSpotTable.select(PostSpotTable.postId, PostSpotTable.catalogId)
            .where { PostSpotTable.postId inList postIds }
            .associate { it[PostSpotTable.postId] to it[PostSpotTable.catalogId] }
    }

    /** 글별로 묶는다(입력 순서). 태그가 없는 글은 키가 없다. */
    override fun findTagsOf(postIds: List<Long>): Map<Long, List<String>> {
        if (postIds.isEmpty()) return emptyMap()
        return PostTagTable.select(PostTagTable.postId, PostTagTable.name)
            .where { PostTagTable.postId inList postIds }
            .orderBy(PostTagTable.id to SortOrder.ASC)
            .groupBy({ it[PostTagTable.postId] }, { it[PostTagTable.name] })
    }

    /** position 순서가 표시 순서다. */
    override fun findPhotoKeysOf(postIds: List<Long>): Map<Long, List<String>> {
        if (postIds.isEmpty()) return emptyMap()
        return PostPhotoTable.select(PostPhotoTable.postId, PostPhotoTable.objectKey)
            .where { PostPhotoTable.postId inList postIds }
            .orderBy(PostPhotoTable.position to SortOrder.ASC)
            .groupBy({ it[PostPhotoTable.postId] }, { it[PostPhotoTable.objectKey] })
    }

    override fun findLikedPostIds(memberId: Long, postIds: List<Long>): Set<Long> {
        if (postIds.isEmpty()) return emptySet()
        return PostLikeTable.select(PostLikeTable.postId)
            .where { (PostLikeTable.memberId eq memberId) and (PostLikeTable.postId inList postIds) }
            .map { it[PostLikeTable.postId] }
            .toSet()
    }

    /** 태그·사진·카테고리·장소·좋아요는 여기서 읽지 않는다(필드 선택 시 DataLoader). */
    private fun postPage(viewer: ViewerContext, first: Int, after: String?, extra: Op<Boolean>): CursorPage<PostView> {
        val cursor = after?.let(::decodeCursor)
        val rows = PostTable.selectAll()
            .where { allOf(extra, visibleToViewer(viewer), cursor?.let { keysetBefore(PostTable.createdAt, PostTable.id, it) }) }
            .orderBy(PostTable.createdAt to SortOrder.DESC, PostTable.id to SortOrder.DESC)
            .limit(first + 1)
            .toList()
        return page(rows, first, cursor != null) { row ->
            CursorEdge(encodeCursor(row[PostTable.createdAt].toInstant(), row[PostTable.id]), row.toPostView(viewer.memberId))
        }
    }

    /** 삭제된 글은 누구에게도, 차단 관계(양방향)인 작성자의 글은 빼고, 숨긴 글은 작성자 본인에게만. */
    private fun visibleToViewer(viewer: ViewerContext): Op<Boolean> = allOf(
        PostTable.deletedAt.isNull(),
        blockExclusion(viewer, PostTable.authorId),
        hiddenExclusion(viewer, PostTable.hidden, PostTable.authorId),
    )

    /** 활성 언어 필터. 장소가 없는 글은 남는다 — 앞 절(장소 연결 없음)을 빠뜨리면 전 언어에서 사라진다. */
    private fun languageCondition(language: ContentLanguage): Op<Boolean> = (PostTable.id notInSubQuery PostSpotTable.select(PostSpotTable.postId)) or
        (
            PostTable.id inSubQuery PostSpotTable
                .join(SpotCatalogLanguageTable, JoinType.INNER, PostSpotTable.catalogId, SpotCatalogLanguageTable.catalogId) {
                    SpotCatalogLanguageTable.language eq language.name
                }
                .select(PostSpotTable.postId)
            )

    /** 개별 숨김 필터. hiddenByViewer면 뒤집어 숨긴 글만 남긴다. */
    private fun hideCondition(search: PostsSearch): Op<Boolean>? {
        val hidden = search.viewer.hiddenPostIds
        return when {
            // 숨긴 글이 없는데 숨긴 글만 달라고 하면 결과도 없어야 한다.
            search.hiddenByViewer && hidden.isEmpty() -> Op.FALSE

            search.hiddenByViewer -> PostTable.id inList hidden

            hidden.isEmpty() -> null

            else -> PostTable.id notInList hidden
        }
    }

    /** 카탈로그로 건다 — 언어가 달라도 같은 장소의 글이 모인다. */
    private fun spotCondition(catalogId: Long): Op<Boolean> = PostTable.id inSubQuery PostSpotTable.select(PostSpotTable.postId).where { PostSpotTable.catalogId eq catalogId }

    /** 다중 선택은 OR(IN)이다. */
    private fun categoryCondition(categories: Set<TargetCategory>): Op<Boolean> = PostTable.id inSubQuery PostChipTable.select(PostChipTable.postId).where { PostChipTable.category inList categories.map { it.name } }

    private fun tagCondition(normalizedTag: String): Op<Boolean> = PostTable.id inSubQuery PostTagTable.select(PostTagTable.postId).where { PostTagTable.normalized eq normalizedTag }

    override fun exists(postId: Long): Boolean = PostTable.select(PostTable.id).where { (PostTable.id eq postId) and PostTable.deletedAt.isNull() }.limit(1).any()
}
