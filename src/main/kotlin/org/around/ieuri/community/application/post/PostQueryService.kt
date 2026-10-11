package org.around.ieuri.community.application.post

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.block.provided.BlockQueryUseCase
import org.around.ieuri.community.application.post.provided.PostQueryUseCase
import org.around.ieuri.community.application.post.provided.dto.CommunityChipView
import org.around.ieuri.community.application.post.provided.dto.MyPostsQuery
import org.around.ieuri.community.application.post.provided.dto.PostView
import org.around.ieuri.community.application.post.provided.dto.PostsQuery
import org.around.ieuri.community.application.post.required.CategoryConfigPort
import org.around.ieuri.community.application.post.required.PostEventPublisherPort
import org.around.ieuri.community.application.post.required.PostQueryPort
import org.around.ieuri.community.application.post.required.dto.MyPostsSearch
import org.around.ieuri.community.application.post.required.dto.PostsArea
import org.around.ieuri.community.application.post.required.dto.PostsSearch
import org.around.ieuri.community.application.shared.PagePolicy
import org.around.ieuri.community.application.shared.required.dto.ViewerContext
import org.around.ieuri.community.domain.post.PostSortOrder
import org.around.ieuri.community.domain.post.PostTag
import org.around.ieuri.community.domain.post.TargetCategory
import org.around.ieuri.community.domain.shared.ContentLanguage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 글 조회. 애그리게이트를 거치지 않고 read DTO를 받는다. 피드·단건은 조회자의 차단·숨김을 조건으로 건다. */
@Service
@Transactional(readOnly = true)
class PostQueryService(
    private val postQueryPort: PostQueryPort,
    private val categoryConfigPort: CategoryConfigPort,
    private val blockQueryUseCase: BlockQueryUseCase,
    private val eventPublisher: PostEventPublisherPort,
    private val pagePolicy: PagePolicy,
    private val policy: PostPolicy,
) : PostQueryUseCase {

    /** 인자 조합이 범위(장소 하나·좌표 반경·카테고리·전체)를 정한다. 거리순은 PostGIS 전용 포트로 간다. */
    override fun posts(query: PostsQuery, memberId: Long, language: ContentLanguage): CursorPage<PostView> {
        // 좌표를 보낸 호출만 위치 조회로 남긴다(SPEC-0032 R19). 좌표 값은 싣지 않는다.
        if (query.hasCoordinates) eventPublisher.publish(LocationAccessed(memberId, "posts"))
        val search = PostsSearch(
            catalogId = query.catalogId,
            area = resolveArea(query),
            categories = query.categories,
            // 대소문자·선행 #을 가리지 않도록 정규화한다.
            normalizedTag = query.tag?.let { PostTag.parseOrNull(it)?.normalized },
            sort = effectiveSort(query),
            hiddenByViewer = query.hiddenByViewer,
            language = language,
            first = pagePolicy.clamp(query.first),
            after = query.after,
            viewer = viewerContext(memberId),
        )
        return when (search.sort) {
            PostSortOrder.LATEST -> postQueryPort.findPosts(search)
            PostSortOrder.DISTANCE -> postQueryPort.findPostsByDistance(search)
        }
    }

    /** 조회수는 여기서만 오르며(피드 목록에서는 오르지 않음) 이벤트로 비동기 증가한다. 못 본 글은 세지 않는다. */
    override fun post(postId: Long, memberId: Long): PostView? {
        val view = postQueryPort.findPost(postId, viewerContext(memberId)) ?: return null
        eventPublisher.publish(PostViewed(postId))
        return view
    }

    /** 조회자 필터를 쓰지 않는다 — 자기가 숨긴 자기 글도 보인다. */
    override fun myPosts(query: MyPostsQuery, memberId: Long): CursorPage<PostView> = postQueryPort.findMyPosts(
        MyPostsSearch(memberId = memberId, first = pagePolicy.clamp(query.first), after = query.after),
    )

    override fun postTags(postIds: List<Long>): Map<Long, List<String>> = postQueryPort.findTagsOf(postIds)

    override fun postPhotoKeys(postIds: List<Long>): Map<Long, List<String>> = postQueryPort.findPhotoKeysOf(postIds)

    override fun postCategories(postIds: List<Long>): Map<Long, TargetCategory> = postQueryPort.findCategoriesOf(postIds)

    override fun postCatalogIds(postIds: List<Long>): Map<Long, Long> = postQueryPort.findCatalogIdsOf(postIds)

    override fun likedPostIds(postIds: List<Long>, memberId: Long): Set<Long> = postQueryPort.findLikedPostIds(memberId, postIds)

    override fun isVisible(postId: Long, memberId: Long): Boolean = postQueryPort.findPost(postId, viewerContext(memberId)) != null

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

    /** 조회자의 양방향 차단 회원과 숨긴 글. 피드·단건이 모두 이 값을 조건으로 건다 — 한 경로라도 빠지면 차단·숨김이 무력화된다. */
    private fun viewerContext(memberId: Long): ViewerContext = ViewerContext(
        memberId = memberId,
        blockedMemberIds = blockQueryUseCase.blockedMemberIds(memberId),
        hiddenPostIds = postQueryPort.findHiddenPostIds(memberId),
    )
}
