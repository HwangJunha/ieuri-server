package org.around.ieuri.community.domain.aggregate

import org.around.ieuri.community.domain.vo.PhotoObjectKey
import java.time.Instant

/**
 * 질문에 달린 답변. 애그리게이트 루트.
 *
 * 질문과 별도 루트인 이유는 답변이 독립적으로 생성·삭제·숨김되고 한 질문에 무제한으로
 * 붙을 수 있기 때문이다.
 *
 * 답변은 질문의 [catalogId]를 복사해 들고 있다. 자격 판정과 목록 조회가 장소 단위이고,
 * 그때마다 질문을 되읽지 않기 위해서다.
 */
class SpotAnswer private constructor(
    val id: Long?,
    val questionId: Long,
    /** 질문이 달린 장소. 질문에서 복사한 값이며 질문이 옮겨 다니지 않으므로 갱신되지 않는다. */
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

    /** 숨겨진 답변은 작성자 본인에게만 보인다. 질문이 숨겨져 함께 가려지는 경우는 조회가 판단한다. */
    fun visibleTo(memberId: Long): Boolean = !deleted && (!hidden || ownedBy(memberId))

    companion object {
        const val BODY_MAX_LENGTH = 500
        const val MAX_PHOTOS = 5

        fun create(
            questionId: Long,
            catalogId: Long,
            authorId: Long,
            body: String,
            photoKeys: List<PhotoObjectKey> = emptyList(),
        ): SpotAnswer {
            require(questionId > 0) { "questionId는 양수여야 합니다." }
            require(catalogId > 0) { "catalogId는 양수여야 합니다." }
            require(authorId > 0) { "authorId는 양수여야 합니다." }
            val trimmed = body.trim()
            require(trimmed.isNotEmpty()) { "본문은 비어 있을 수 없습니다." }
            require(trimmed.length <= BODY_MAX_LENGTH) { "본문은 ${BODY_MAX_LENGTH}자를 넘을 수 없습니다." }
            require(photoKeys.size <= MAX_PHOTOS) { "사진은 최대 ${MAX_PHOTOS}장입니다." }
            require(photoKeys.all { it.ownedBy(authorId) }) { "본인에게 발급된 사진 키가 아닙니다." }
            require(photoKeys.all { it.confirmed }) { "업로드 확인을 마치지 않은 사진 키입니다." }
            return SpotAnswer(
                id = null,
                questionId = questionId,
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
            questionId: Long,
            catalogId: Long,
            authorId: Long,
            body: String,
            photoKeys: List<PhotoObjectKey>,
            hidden: Boolean,
            deletedAt: Instant?,
            createdAt: Instant,
        ): SpotAnswer = SpotAnswer(
            id = id,
            questionId = questionId,
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
