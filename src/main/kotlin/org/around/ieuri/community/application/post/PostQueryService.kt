package org.around.ieuri.community.application.post

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.post.provided.PostQueryUseCase
import org.around.ieuri.community.application.shared.CommunityPolicy
import org.around.ieuri.community.application.shared.clampPageSize
import org.around.ieuri.community.application.shared.dto.CommunityChipView
import org.around.ieuri.community.application.shared.dto.MyPostsQuery
import org.around.ieuri.community.application.shared.dto.PostView
import org.around.ieuri.community.application.shared.dto.PostsQuery
import org.around.ieuri.community.application.shared.required.CategoryConfigPort
import org.around.ieuri.community.application.shared.required.CommunityEventPublisherPort
import org.around.ieuri.community.application.shared.required.CommunityQueryPort
import org.around.ieuri.community.application.shared.required.MyPostsSearch
import org.around.ieuri.community.application.shared.required.PostSpatialQueryPort
import org.around.ieuri.community.application.shared.required.PostsArea
import org.around.ieuri.community.application.shared.required.PostsSearch
import org.around.ieuri.community.application.shared.viewerContext
import org.around.ieuri.community.domain.post.PostTag
import org.around.ieuri.community.domain.shared.ContentLanguage
import org.around.ieuri.community.domain.shared.PostSortOrder
import org.around.ieuri.community.domain.shared.TargetCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 글 조회. 애그리게이트를 거치지 않고 read DTO를 받는다. 피드·단건은 조회자의 차단·숨김을 조건으로 건다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class PostQueryService(
    private val communityQueryPort: CommunityQueryPort,
    private val postSpatialQueryPort: PostSpatialQueryPort,
    private val categoryConfigPort: CategoryConfigPort,
    private val eventPublisher: CommunityEventPublisherPort,
    private val policy: CommunityPolicy,
) : PostQueryUseCase {

    /** 인자 조합이 범위(장소 하나·좌표 반경·카테고리·전체)를 정한다. 거리순은 PostGIS 전용 포트로 간다. */
    override fun posts(query: PostsQuery, memberId: Long, language: ContentLanguage): CursorPage<PostView> {
        val search = PostsSearch(
            catalogId = query.catalogId,
            area = resolveArea(query),
            categories = query.categories,
            // 대소문자·선행 #을 가리지 않도록 정규화한다.
            normalizedTag = query.tag?.let { PostTag.parseOrNull(it)?.normalized },
            sort = effectiveSort(query),
            hiddenByViewer = query.hiddenByViewer,
            language = language,
            first = policy.clampPageSize(query.first),
            after = query.after,
            viewer = communityQueryPort.viewerContext(memberId),
        )
        return when (search.sort) {
            PostSortOrder.LATEST -> communityQueryPort.findPosts(search)
            PostSortOrder.DISTANCE -> postSpatialQueryPort.findPostsByDistance(search)
        }
    }

    /** 조회수는 여기서만 오르며(피드 목록에서는 오르지 않음) 이벤트로 비동기 증가한다. 못 본 글은 세지 않는다. */
    override fun post(postId: Long, memberId: Long): PostView? {
        val view = communityQueryPort.findPost(postId, communityQueryPort.viewerContext(memberId)) ?: return null
        eventPublisher.publish(PostViewed(postId))
        return view
    }

    /** 조회자 필터를 쓰지 않는다 — 자기가 숨긴 자기 글도 보인다. */
    override fun myPosts(query: MyPostsQuery, memberId: Long): CursorPage<PostView> = communityQueryPort.findMyPosts(
        MyPostsSearch(memberId = memberId, first = policy.clampPageSize(query.first), after = query.after),
    )

    override fun postTags(postIds: List<Long>): Map<Long, List<String>> = communityQueryPort.findTagsOf(postIds)

    override fun postPhotoKeys(postIds: List<Long>): Map<Long, List<String>> = communityQueryPort.findPhotoKeysOf(postIds)

    override fun postCategories(postIds: List<Long>): Map<Long, TargetCategory> = communityQueryPort.findCategoriesOf(postIds)

    override fun postCatalogIds(postIds: List<Long>): Map<Long, Long> = communityQueryPort.findCatalogIdsOf(postIds)

    override fun likedPostIds(postIds: List<Long>, memberId: Long): Set<Long> = communityQueryPort.findLikedPostIds(memberId, postIds)

    /** 칩 구성은 tourism의 `app_category`가 소유한다. */
    override fun communityChips(appKey: AppKey, language: ContentLanguage): List<CommunityChipView> = categoryConfigPort.findCategories(appKey, language)

    /** 좌표가 없으면 거리순 요청도 오류 없이 최신순으로 떨어진다. */
    private fun effectiveSort(query: PostsQuery): PostSortOrder = if (query.sort == PostSortOrder.DISTANCE && !query.hasCoordinates) PostSortOrder.LATEST else query.sort

    /** 좌표가 없으면 null이고 반경으로 좁히지 않는다. 반경 상한 초과는 절삭이다. */
    private fun resolveArea(query: PostsQuery): PostsArea? {
        if (!query.hasCoordinates) return null
        return PostsArea(
            longitude = requireNotNull(query.longitude),
            latitude = requireNotNull(query.latitude),
            radiusMeters = query.radiusMeters.coerceIn(1, policy.maxRadiusMeters),
        )
    }
}
