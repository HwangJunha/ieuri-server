package org.around.ieuri.community.application.post.required

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.post.provided.dto.PostView
import org.around.ieuri.community.application.post.required.dto.MyPostsSearch
import org.around.ieuri.community.application.post.required.dto.PostsSearch
import org.around.ieuri.community.application.shared.required.dto.ViewerContext
import org.around.ieuri.community.domain.post.TargetCategory

/** 글 읽기 포트(CQRS 읽기). 애그리게이트를 우회해 필요한 값만 프로젝션한다.. */
interface PostQueryPort {
    /** 최신순 피드. */
    fun findPosts(search: PostsSearch): CursorPage<PostView>

    /**
     * 반경 안의 글을 가까운 순으로 한 페이지. 같은 거리면 최신순이다. 좌표([PostsSearch.area])가 있어야 한다.
     * - 모더레이션·카테고리·태그 필터를 이 조회가 함께 걸어, 요청 개수가 곧 페이지 크기다.
     */
    fun findPostsByDistance(search: PostsSearch): CursorPage<PostView>

    /** 글 단건. 피드와 같은 모더레이션 필터를 거치며, 걸러지면 null이다. */
    fun findPost(postId: Long, viewer: ViewerContext): PostView?

    /**
     * 본인이 작성한 글(SPEC-0011 R3). 작성 시각 내림차순.
     * - 모더레이션 필터를 거치지 않는다(자기가 숨긴 글도 나온다). 삭제된 글만 제외한다.
     */
    fun findMyPosts(search: MyPostsSearch): CursorPage<PostView>

    /** [memberId]가 개별로 숨긴 글의 id. 피드 필터가 이 집합을 제외한다. */
    fun findHiddenPostIds(memberId: Long): Set<Long>

    /** 카테고리는 모든 글에 있다 — 키가 없으면 연결이 깨진 것이다. */
    fun findCategoriesOf(postIds: List<Long>): Map<Long, TargetCategory>

    /** 글이 가리키는 카탈로그 id(SPEC-0019). 장소 없는 글은 정상이며 결과에 키가 없다. */
    fun findCatalogIdsOf(postIds: List<Long>): Map<Long, Long>

    /** 글 여러 건의 태그를 한 번에(DataLoader용). 결과에 키가 없으면 그 글에는 값이 없다. */
    fun findTagsOf(postIds: List<Long>): Map<Long, List<String>>

    /** 첨부 순서대로. 사진이 없는 글은 결과에 키가 없다. */
    fun findPhotoKeysOf(postIds: List<Long>): Map<Long, List<String>>

    /** [memberId]가 좋아요한 글의 id만. 차단은 이 판정에 영향을 주지 않는다. */
    fun findLikedPostIds(memberId: Long, postIds: List<Long>): Set<Long>
}
