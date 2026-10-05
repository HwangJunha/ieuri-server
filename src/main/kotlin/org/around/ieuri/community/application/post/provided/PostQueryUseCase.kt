package org.around.ieuri.community.application.post.provided

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.shared.dto.CommunityChipView
import org.around.ieuri.community.application.shared.dto.MyPostsQuery
import org.around.ieuri.community.application.shared.dto.PostView
import org.around.ieuri.community.application.shared.dto.PostsQuery
import org.around.ieuri.community.domain.shared.ContentLanguage
import org.around.ieuri.community.domain.shared.TargetCategory

/** 글 조회. 피드와 단건이 같은 모더레이션 필터를 거친다. 모든 조회가 인증을 전제한다. */
interface PostQueryUseCase {
    /** 범위는 [PostsQuery]의 인자 조합이 정한다. [language]에서 해소되지 않는 장소가 붙은 글은 빠지고, 장소 없는 글은 남는다. */
    fun posts(query: PostsQuery, memberId: Long, language: ContentLanguage): CursorPage<PostView>

    /** 차단·숨김에 걸리면 오류가 아니라 null이다. 작성자 본인만 예외다. */
    fun post(postId: Long, memberId: Long): PostView?

    /** 본인이 작성한 글. 작성 시각 내림차순이며 숨김·차단 필터를 거치지 않는다. */
    fun myPosts(query: MyPostsQuery, memberId: Long): CursorPage<PostView>

    // 글의 지연 필드(DataLoader용). 결과에 키가 없으면 그 글에 값이 없다는 뜻이다.
    fun postTags(postIds: List<Long>): Map<Long, List<String>>

    fun postPhotoKeys(postIds: List<Long>): Map<Long, List<String>>

    fun postCategories(postIds: List<Long>): Map<Long, TargetCategory>

    /** 장소는 선택이라 없는 글이 정상이다. 값은 카탈로그 id다. */
    fun postCatalogIds(postIds: List<Long>): Map<Long, Long>

    /** 호출자가 좋아요한 글의 id만. */
    fun likedPostIds(postIds: List<Long>, memberId: Long): Set<Long>

    /** 앱·언어별 상단 카테고리 칩. 구성이 없는 앱이면 오류가 아니라 빈 목록이다. */
    fun communityChips(appKey: AppKey, language: ContentLanguage): List<CommunityChipView>
}
