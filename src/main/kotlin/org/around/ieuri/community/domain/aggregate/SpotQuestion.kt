package org.around.ieuri.community.domain.aggregate

import org.around.ieuri.community.domain.vo.PhotoObjectKey
import java.time.Instant

/**
 * 장소에 달린 질문. 애그리게이트 루트.
 *
 * 글([Post])과 달리 [catalogId]가 필수다 — 질문은 언제나 어떤 장소에 대한 것이다.
 *
 * **[resolved]는 한 방향으로만 간다.** 되돌리는 수단을 두지 않았고, 그 뜻은 "이 답이 정답"이
 * 아니라 "질문자가 충분히 알게 되었다"이다.
 *
 * 좋아요 자격은 이 모델이 모른다 — 다른 애그리게이트(SpotReaction)의 상태이며 응용 계층이
 * 쓰기 시점에 판정한다.
 */
class SpotQuestion private constructor(
    val id: Long?,
    val catalogId: Long,
    val authorId: Long,
    val body: String,
    photoKeys: List<PhotoObjectKey>,
    resolved: Boolean,
    answerCount: Int,
    hidden: Boolean,
    deletedAt: Instant?,
    val createdAt: Instant?,
) {
    var photoKeys: List<PhotoObjectKey> = photoKeys
        private set

    var resolved: Boolean = resolved
        private set

    /** 숨겨진 답변을 제외한 답변 수. 신고로 답변이 가려지면 줄어든다. */
    var answerCount: Int = answerCount
        private set

    var hidden: Boolean = hidden
        private set

    var deletedAt: Instant? = deletedAt
        private set

    val deleted: Boolean get() = deletedAt != null

    /** 작성자 본인만 해결 표시를 할 수 있다. 이미 해결됨이어도 true다 — 멱등이다. */
    fun resolveBy(memberId: Long): Boolean {
        if (authorId != memberId) return false
        resolved = true
        return true
    }

    fun increaseAnswerCount() {
        answerCount += 1
    }

    fun decreaseAnswerCount() {
        if (answerCount > 0) answerCount -= 1
    }

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

    /** 숨겨진 질문은 작성자 본인에게만 보인다. */
    fun visibleTo(memberId: Long): Boolean = !deleted && (!hidden || ownedBy(memberId))

    companion object {
        const val BODY_MAX_LENGTH = 500
        const val MAX_PHOTOS = 5

        fun create(
            catalogId: Long,
            authorId: Long,
            body: String,
            photoKeys: List<PhotoObjectKey> = emptyList(),
        ): SpotQuestion {
            require(catalogId > 0) { "catalogId는 양수여야 합니다." }
            require(authorId > 0) { "authorId는 양수여야 합니다." }
            val trimmed = body.trim()
            require(trimmed.isNotEmpty()) { "본문은 비어 있을 수 없습니다." }
            require(trimmed.length <= BODY_MAX_LENGTH) { "본문은 ${BODY_MAX_LENGTH}자를 넘을 수 없습니다." }
            require(photoKeys.size <= MAX_PHOTOS) { "사진은 최대 ${MAX_PHOTOS}장입니다." }
            require(photoKeys.all { it.ownedBy(authorId) }) { "본인에게 발급된 사진 키가 아닙니다." }
            require(photoKeys.all { it.confirmed }) { "업로드 확인을 마치지 않은 사진 키입니다." }
            return SpotQuestion(
                id = null,
                catalogId = catalogId,
                authorId = authorId,
                body = trimmed,
                photoKeys = photoKeys,
                resolved = false,
                answerCount = 0,
                hidden = false,
                deletedAt = null,
                createdAt = null,
            )
        }

        fun restore(
            id: Long,
            catalogId: Long,
            authorId: Long,
            body: String,
            photoKeys: List<PhotoObjectKey>,
            resolved: Boolean,
            answerCount: Int,
            hidden: Boolean,
            deletedAt: Instant?,
            createdAt: Instant,
        ): SpotQuestion = SpotQuestion(
            id = id,
            catalogId = catalogId,
            authorId = authorId,
            body = body,
            photoKeys = photoKeys,
            resolved = resolved,
            answerCount = answerCount,
            hidden = hidden,
            deletedAt = deletedAt,
            createdAt = createdAt,
        )
    }
}
