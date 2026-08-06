package org.around.ieuri.community.domain.aggregate

import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.domain.vo.PostTag
import org.around.ieuri.community.domain.vo.SpotPoint
import java.time.Instant

/**
 * 커뮤니티 글. 애그리게이트 루트.
 *
 * 글은 [category]에 반드시 속하고 [spotId]에는 선택적으로 붙는다 — 장소를 고르지 않아도
 * 카테고리만으로 글을 쓸 수 있다.
 *
 * likeCount·commentCount는 파생값이지만 애그리게이트가 들고 있다 — 매 조회마다 세지 않는다.
 * 갱신은 좋아요·댓글·신고 커맨드가 이 애그리게이트를 거쳐서 한다.
 *
 * Comment는 별도 애그리게이트다. 댓글 수가 늘어도 글을 통째로 적재할 이유가 없다.
 */
class Post private constructor(
    val id: Long?,
    /** 글의 카테고리. 상단 칩이 이 값으로 거른다. spot 유무와 무관하게 항상 있다. */
    val category: TargetCategory,
    /** 연결된 장소. **선택이다** — 장소 없이 카테고리만으로도 글을 쓸 수 있다. */
    val spotId: Long?,
    /**
     * 장소의 좌표. [spotId]가 있을 때만 있다. 적재 시 복사해 두는 값이며 — 지역 피드가
     * tourism에 spot 목록을 되묻지 않게 한다 — 조회 응답에는 쓰이지 않는다.
     */
    val spotPoint: SpotPoint?,
    val authorId: Long,
    val tags: List<PostTag>,
    title: String,
    body: String,
    photoKeys: List<PhotoObjectKey>,
    likeCount: Int,
    commentCount: Int,
    hidden: Boolean,
    deletedAt: Instant?,
    val createdAt: Instant?,
) {
    var title: String = title
        private set

    var body: String = body
        private set

    var photoKeys: List<PhotoObjectKey> = photoKeys
        private set

    /** 수정된 적이 있는지. 클라이언트가 "수정됨" 표시에 쓴다. */
    var edited: Boolean = false
        private set

    var likeCount: Int = likeCount
        private set

    var commentCount: Int = commentCount
        private set

    var hidden: Boolean = hidden
        private set

    var deletedAt: Instant? = deletedAt
        private set

    val deleted: Boolean get() = deletedAt != null

    /** 좋아요 반영. 멱등성은 저장소의 유일 제약이 판정하고, 여기서는 집계만 올린다. */
    fun increaseLikeCount() {
        likeCount += 1
    }

    /** 좋아요 취소 반영. 0 아래로 내려가지 않는다. */
    fun decreaseLikeCount() {
        if (likeCount > 0) likeCount -= 1
    }

    fun increaseCommentCount() {
        commentCount += 1
    }

    /**
     * 댓글이 숨겨지거나 삭제될 때 함께 내린다. 숨겨진 댓글은 댓글 수에 포함되지 않으며,
     * 모든 호출자에게 같은 값이어야 한다 — 자기 댓글이 숨겨진 사용자에게만 다른 숫자를
     * 보여주지 않는다.
     */
    fun decreaseCommentCount() {
        if (commentCount > 0) commentCount -= 1
    }

    /**
     * 제목·본문을 고친다. **작성자 본인만**, 그리고 **숨겨지지 않은 글만** 고칠 수 있다.
     *
     * 숨겨진 글을 막는 이유는 신고 기록이 대상 글을 가리키기 때문이다 — 신고가 쌓여 숨겨진 글을
     * 다른 내용으로 바꿔치기하면 그 기록이 사유와 무관한 글을 가리키게 되고, 검토 도구가 나중에
     * 보는 것이 증거로서 의미를 잃는다.
     *
     * 사진·주제·대상은 바꿀 수 없다 — 사진은 R2 확인 흐름을 다시 타야 하고, 대상이 바뀌면 글이
     * 다른 장소의 피드로 순간이동한다.
     *
     * 소유자가 아니거나 숨겨진 글이면 false를 돌려주고 상태를 바꾸지 않는다 — 예외 종류 선택은
     * 애플리케이션 계층의 몫이다.
     */
    fun editBy(memberId: Long, newTitle: String, newBody: String): Boolean {
        if (authorId != memberId || hidden || deleted) return false
        val trimmedTitle = newTitle.trim()
        val trimmedBody = newBody.trim()
        require(trimmedTitle.isNotEmpty()) { "제목은 비어 있을 수 없습니다." }
        require(trimmedTitle.length <= TITLE_MAX_LENGTH) { "제목은 ${TITLE_MAX_LENGTH}자를 넘을 수 없습니다." }
        require(trimmedBody.isNotEmpty()) { "본문은 비어 있을 수 없습니다." }
        require(trimmedBody.length <= BODY_MAX_LENGTH) { "본문은 ${BODY_MAX_LENGTH}자를 넘을 수 없습니다." }

        title = trimmedTitle
        body = trimmedBody
        edited = true
        return true
    }

    /**
     * 사진을 **전체 목록으로 교체한다.** 빠진 키는 글에서 떨어지고 새 키는 붙는다.
     *
     * 떨어진 사진의 오브젝트는 스토리지에 남는다 — 지우는 것은 클라이언트가 deletePostImage로
     * 할 일이다. 여기서 지우면 같은 키를 다른 글이 쓰고 있을 때 그 글의 사진이 깨진다.
     */
    fun replacePhotos(newPhotoKeys: List<PhotoObjectKey>) {
        require(newPhotoKeys.size <= MAX_PHOTOS) { "사진은 최대 ${MAX_PHOTOS}장입니다." }
        require(newPhotoKeys.all { it.ownedBy(authorId) }) { "본인에게 발급된 사진 키가 아닙니다." }
        require(newPhotoKeys.all { it.confirmed }) { "업로드 확인을 마치지 않은 사진 키입니다." }
        photoKeys = newPhotoKeys
    }

    /** 신고 누적으로 숨긴다. 이미 숨겨진 글에 다시 불려도 안전하다. */
    fun hide() {
        hidden = true
    }

    /**
     * 작성자 본인만 삭제할 수 있다. 소유자가 아니면 false를 돌려주고 상태를 바꾸지 않는다 —
     * 예외 종류 선택은 애플리케이션 계층의 몫이다.
     */
    fun deleteBy(memberId: Long, now: Instant): Boolean {
        if (authorId != memberId) return false
        deletedAt = now
        return true
    }

    fun ownedBy(memberId: Long): Boolean = authorId == memberId

    /**
     * 호출자에게 이 글이 보이는지. 피드·단건 조회가 공유하는 판정이다.
     * 숨겨진 글은 작성자 본인에게만 남는다.
     */
    fun visibleTo(memberId: Long): Boolean = !deleted && (!hidden || ownedBy(memberId))

    companion object {
        const val TITLE_MAX_LENGTH = 50
        const val BODY_MAX_LENGTH = 500
        const val MAX_PHOTOS = 5

        /**
         * 새 글. 본문은 트리밍 후 판정하므로 공백만 있는 본문은 빈 문자열로 거부된다.
         *
         * 사진 키의 **소유권**만 여기서 본다 — 실제 업로드 여부는 저장소에 물어야 하므로
         * 애플리케이션 계층의 책임이다. 남의 키인지 확인하는 데 저장소를 왕복할 이유가 없다.
         */
        fun create(
            category: TargetCategory,
            spotId: Long?,
            spotPoint: SpotPoint?,
            authorId: Long,
            tags: List<PostTag>,
            title: String,
            body: String,
            photoKeys: List<PhotoObjectKey>,
        ): Post {
            require(authorId > 0) { "authorId는 양수여야 합니다." }
            require(spotId == null || spotId > 0) { "spotId는 양수여야 합니다." }
            // 장소가 있으면 좌표도 있어야 한다. 없으면 그 글이 지역 피드에서 조용히 사라진다.
            require((spotId == null) == (spotPoint == null)) {
                "장소와 좌표는 함께 있거나 함께 없어야 합니다."
            }
            val trimmedTitle = title.trim()
            require(trimmedTitle.isNotEmpty()) { "제목은 비어 있을 수 없습니다." }
            require(trimmedTitle.length <= TITLE_MAX_LENGTH) { "제목은 ${TITLE_MAX_LENGTH}자를 넘을 수 없습니다." }
            val trimmed = body.trim()
            require(trimmed.isNotEmpty()) { "본문은 비어 있을 수 없습니다." }
            require(trimmed.length <= BODY_MAX_LENGTH) { "본문은 ${BODY_MAX_LENGTH}자를 넘을 수 없습니다." }
            require(photoKeys.size <= MAX_PHOTOS) { "사진은 최대 ${MAX_PHOTOS}장입니다." }
            require(photoKeys.all { it.ownedBy(authorId) }) { "본인에게 발급된 사진 키가 아닙니다." }
            require(photoKeys.all { it.confirmed }) { "업로드 확인을 마치지 않은 사진 키입니다." }
            require(tags.size <= PostTag.MAX_PER_POST) { "태그는 최대 ${PostTag.MAX_PER_POST}개입니다." }
            // 같은 태그를 두 번 붙여도 하나로 본다 — 유저가 "서울"과 "#서울"을 함께 칠 수 있다.
            val distinctTags = tags.distinctBy { it.normalized }
            return Post(
                id = null,
                category = category,
                spotId = spotId,
                spotPoint = spotPoint,
                authorId = authorId,
                tags = distinctTags,
                title = trimmedTitle,
                body = trimmed,
                photoKeys = photoKeys,
                likeCount = 0,
                commentCount = 0,
                hidden = false,
                deletedAt = null,
                createdAt = null,
            )
        }

        /** 저장소에서 복원. 불변식은 생성 시점에 이미 통과했으므로 다시 검사하지 않는다. */
        fun restore(
            id: Long,
            category: TargetCategory,
            spotId: Long?,
            authorId: Long,
            spotPoint: SpotPoint? = null,
            tags: List<PostTag>,
            title: String,
            body: String,
            photoKeys: List<PhotoObjectKey>,
            likeCount: Int,
            commentCount: Int,
            hidden: Boolean,
            deletedAt: Instant?,
            createdAt: Instant,
            edited: Boolean = false,
        ): Post = Post(
            id = id,
            category = category,
            spotId = spotId,
            spotPoint = spotPoint,
            authorId = authorId,
            tags = tags,
            title = title,
            body = body,
            photoKeys = photoKeys,
            likeCount = likeCount,
            commentCount = commentCount,
            hidden = hidden,
            deletedAt = deletedAt,
            createdAt = createdAt,
        ).also { it.edited = edited }
    }
}
