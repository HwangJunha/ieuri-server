package org.around.ieuri.community.domain.post

import org.around.ieuri.community.domain.shared.PhotoNotUploadedException
import org.around.ieuri.community.domain.shared.TooManyPhotosException
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.SpotPoint
import java.time.Instant

/** 글이 가리키는 장소. 카탈로그 id(언어별 `spot.id`가 아니다)와 적재 시 복사한 좌표는 함께 있다. */
data class PostSpot(val catalogId: Long, val point: SpotPoint) {
    init {
        require(catalogId > 0) { "catalogId는 양수여야 합니다." }
    }
}

/** 편집에서 장소를 바꾸는 방식. 장소를 건드리지 않으면 이 값을 넘기지 않는다(null). */
sealed interface SpotChange {
    data class Set(val spot: PostSpot) : SpotChange

    data object Remove : SpotChange
}

/** 글의 태그 목록. 최대 [PostTag.MAX_PER_POST]개이며 정규화가 같은 태그는 하나로 본다. */
@ConsistentCopyVisibility
data class PostTags private constructor(val values: List<PostTag>) {
    companion object {
        fun of(tags: List<PostTag>): PostTags {
            if (tags.size > PostTag.MAX_PER_POST) throw TooManyTagsException()
            return PostTags(tags.distinctBy { it.normalized })
        }
    }
}

/** 글의 사진 목록(표시 순서). 본인에게 발급되고 업로드 확인(`images/`)을 마친 키만 붙일 수 있다. */
@ConsistentCopyVisibility
data class PostPhotos private constructor(val keys: List<PhotoObjectKey>) {
    companion object {
        const val MAX_PHOTOS = 5

        /** 실제 오브젝트 존재는 애플리케이션이 확인한다. */
        fun of(keys: List<PhotoObjectKey>, authorId: Long): PostPhotos {
            if (keys.size > MAX_PHOTOS) throw TooManyPhotosException()
            if (!keys.all { it.ownedBy(authorId) && it.confirmed }) throw PhotoNotUploadedException()
            return PostPhotos(keys)
        }
    }
}

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
