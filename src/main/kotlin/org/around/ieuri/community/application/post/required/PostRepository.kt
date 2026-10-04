package org.around.ieuri.community.application.post.required

import org.around.ieuri.community.domain.post.Post
import org.around.ieuri.community.domain.post.PostPhotos
import org.around.ieuri.community.domain.post.PostSpot
import org.around.ieuri.community.domain.post.PostTags
import org.around.ieuri.community.domain.vo.TargetCategory
import java.time.Instant

/**
 * 글 저장소. 연산마다 쓰는 열을 나눠 동시 요청이 서로의 변경을 덮어쓰지 않게 한다.
 * - 편집은 내용 열만, 숨김·삭제는 각자 한 열만, 계수는 원자적 증감만 쓴다.
 * - 카테고리·장소·태그·사진은 바꿀 때만 그 행을 쓴다.
 */
interface PostRepository {
    /** 삭제되지 않은 글 한 행만 읽는다. 없으면 null. */
    fun findById(postId: Long): Post?

    /** 삭제되지 않은 글이 있는지. */
    fun exists(postId: Long): Boolean

    /** 새 글과 그 카테고리·장소·태그·사진을 넣고 id를 돌려준다. */
    fun insert(post: Post, category: TargetCategory, spot: PostSpot?, tags: PostTags, photos: PostPhotos): Long

    /** 제목·본문·편집 표시만 쓴다. 그 사이 숨겨졌거나 삭제됐으면 0건이고 false. */
    fun updateContent(post: Post): Boolean

    fun changeCategory(postId: Long, category: TargetCategory)

    /** null이면 장소를 뗀다. */
    fun changeSpot(postId: Long, spot: PostSpot?)

    /** 태그 전체를 이 목록으로 바꾼다. 빈 목록이면 모두 뗀다. */
    fun replaceTags(postId: Long, tags: PostTags)

    /** 사진 전체를 이 목록으로 바꾼다. 빈 목록이면 모두 뗀다. */
    fun replacePhotos(postId: Long, photos: PostPhotos)

    /** 신고 누적 숨김. 보이는(숨겨지지도 삭제되지도 않은) 글만 바꾸며, 이번 호출로 숨겨졌으면 true. */
    fun hideIfVisible(postId: Long): Boolean

    /** 이미 삭제됐으면 false. */
    fun markDeleted(postId: Long, deletedAt: Instant): Boolean

    /** 내 화면 숨김. 새로 숨겼으면 true(멱등). */
    fun addHide(memberId: Long, postId: Long): Boolean

    fun removeHide(memberId: Long, postId: Long): Boolean

    /** 새로 좋아요했으면 true, 이미 했으면 false(멱등). */
    fun addLike(postId: Long, memberId: Long): Boolean

    /** 실제로 지웠으면 true. */
    fun removeLike(postId: Long, memberId: Long): Boolean

    /** 삭제됐거나 없으면 0. */
    fun likeCount(postId: Long): Int

    // 계수: 삭제된 글은 바꾸지 않고, 0 아래로 내려가지 않는다. 대상이 없어도 오류가 아니다.
    fun increaseLikeCount(postId: Long)

    fun decreaseLikeCount(postId: Long)

    fun increaseCommentCount(postId: Long)

    fun decreaseCommentCount(postId: Long)

    fun increaseViewCount(postId: Long)
}
