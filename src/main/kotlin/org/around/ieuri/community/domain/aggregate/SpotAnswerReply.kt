package org.around.ieuri.community.domain.aggregate

import org.around.ieuri.community.domain.vo.PhotoObjectKey
import java.time.Instant

/**
 * 답변에 달린 답글. 애그리게이트 루트.
 *
 * **답글에는 답글을 달 수 없다.** 부모 답글 id 필드가 없는 것이 그 표현이며, 3단계 상태를
 * 도메인에서 표현할 수 없게 만든다. 댓글([Comment])이 3단계 시도를 2단계로 눕히는 것과
 * 다른 선택이다 — 여기서는 계약이 거부를 요구한다(REPLY_TO_REPLY_NOT_ALLOWED).
 *
 * 답변과 별도 루트인 이유는 답글이 독립적으로 신고·숨김·삭제되기 때문이다. 내부 엔티티라면
 * 답글 하나를 지우는 데 답변 애그리게이트 전체를 적재해야 한다.
 */
class SpotAnswerReply private constructor(
    val id: Long?,
    val answerId: Long,
    /** 자격 판정이 장소 단위라 답변에서 복사해 들고 있다. */
    val catalogId: Long,
    val authorId: Long,
    val body: String,
    photoKeys: List<PhotoObjectKey>,
    hidden: Boolean,
    deletedAt: Instant?,
    val createdAt: Instant?,
) {
    var photoKeys: List<PhotoObjectKey> = photoKeys
        private set

    var hidden: Boolean = hidden
        private set

    var deletedAt: Instant? = deletedAt
        private set

    val deleted: Boolean get() = deletedAt != null

    fun hide() {
        hidden = true
    }

    /** 작성자 본인만 삭제할 수 있다. 소유자가 아니면 false이며 상태는 그대로다. */
    fun deleteBy(memberId: Long, now: Instant): Boolean {
        if (authorId != memberId) return false
        deletedAt = now
        return true
    }

    fun ownedBy(memberId: Long): Boolean = authorId == memberId

    /** 숨겨진 답글은 작성자 본인에게만 보인다. */
    fun visibleTo(memberId: Long): Boolean = !deleted && (!hidden || ownedBy(memberId))

    companion object {
        const val BODY_MAX_LENGTH = 500
        const val MAX_PHOTOS = 5

        fun create(
            answerId: Long,
            catalogId: Long,
            authorId: Long,
            body: String,
            photoKeys: List<PhotoObjectKey> = emptyList(),
        ): SpotAnswerReply {
            require(answerId > 0) { "answerId는 양수여야 합니다." }
            require(catalogId > 0) { "catalogId는 양수여야 합니다." }
            require(authorId > 0) { "authorId는 양수여야 합니다." }
            val trimmed = body.trim()
            require(trimmed.isNotEmpty()) { "본문은 비어 있을 수 없습니다." }
            require(trimmed.length <= BODY_MAX_LENGTH) { "본문은 ${BODY_MAX_LENGTH}자를 넘을 수 없습니다." }
            require(photoKeys.size <= MAX_PHOTOS) { "사진은 최대 ${MAX_PHOTOS}장입니다." }
            require(photoKeys.all { it.ownedBy(authorId) }) { "본인에게 발급된 사진 키가 아닙니다." }
            require(photoKeys.all { it.confirmed }) { "업로드 확인을 마치지 않은 사진 키입니다." }
            return SpotAnswerReply(
                id = null,
                answerId = answerId,
                catalogId = catalogId,
                authorId = authorId,
                body = trimmed,
                photoKeys = photoKeys,
                hidden = false,
                deletedAt = null,
                createdAt = null,
            )
        }

        fun restore(
            id: Long,
            answerId: Long,
            catalogId: Long,
            authorId: Long,
            body: String,
            photoKeys: List<PhotoObjectKey>,
            hidden: Boolean,
            deletedAt: Instant?,
            createdAt: Instant,
        ): SpotAnswerReply = SpotAnswerReply(
            id = id,
            answerId = answerId,
            catalogId = catalogId,
            authorId = authorId,
            body = body,
            photoKeys = photoKeys,
            hidden = hidden,
            deletedAt = deletedAt,
            createdAt = createdAt,
        )
    }
}
