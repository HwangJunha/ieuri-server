package org.around.ieuri.community.domain.post

import java.time.Instant

/**
 * 커뮤니티 글. 애그리게이트 루트이며 글 한 행만 담는다 — 카테고리·장소·태그·사진은 값 객체로 따로 저장한다.
 * - 좋아요·댓글·조회 수는 상태가 아니다 — 저장소가 원자적으로 증감한다.
 * - 숨김(신고 누적)·삭제는 저장소의 조건부 UPDATE로 바뀐다. 편집은 그 두 열을 쓰지 않는다.
 */
data class Post(
    val id: Long?,
    val authorId: Long,
    val title: String,
    val body: String,
    val edited: Boolean,
    val hidden: Boolean,
    val deletedAt: Instant?,
) {
    /** 작성자만, 숨겨지지 않은 글만 고칠 수 있다. 제목·본문은 매번 함께 바뀐다. */
    fun edit(memberId: Long, title: String, body: String): Post {
        if (authorId != memberId) throw NotPostOwnerException()
        if (hidden) throw HiddenPostNotEditableException()
        return copy(title = validTitle(title), body = validBody(body), edited = true)
    }

    /** 작성자만 삭제할 수 있다. */
    fun delete(memberId: Long, now: Instant): Post {
        if (authorId != memberId) throw NotPostOwnerException()
        return copy(deletedAt = now)
    }

    companion object {
        const val TITLE_MAX_LENGTH = 50
        const val BODY_MAX_LENGTH = 500

        /** 새 글. 제목·본문은 트리밍 후 판정하므로 공백만 있으면 거부된다. */
        fun write(authorId: Long, title: String, body: String): Post {
            require(authorId > 0) { "authorId는 양수여야 합니다." }
            return Post(
                id = null,
                authorId = authorId,
                title = validTitle(title),
                body = validBody(body),
                edited = false,
                hidden = false,
                deletedAt = null,
            )
        }

        private fun validTitle(raw: String): String {
            val title = raw.trim()
            if (title.isEmpty()) throw PostTitleRequiredException()
            if (title.length > TITLE_MAX_LENGTH) throw PostTitleLengthInvalidException()
            return title
        }

        private fun validBody(raw: String): String {
            val body = raw.trim()
            if (body.isEmpty()) throw PostBodyRequiredException()
            if (body.length > BODY_MAX_LENGTH) throw PostBodyLengthInvalidException()
            return body
        }
    }
}
