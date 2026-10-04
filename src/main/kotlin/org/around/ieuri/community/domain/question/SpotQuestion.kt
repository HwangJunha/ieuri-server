package org.around.ieuri.community.domain.question

import org.around.ieuri.community.domain.shared.SpotQaText
import java.time.Instant

/**
 * 장소에 달린 질문. 애그리게이트 루트이며 질문 한 행만 담는다 — 사진은 값 객체로 따로 저장한다.
 * - 답변 수는 상태가 아니다 — 저장소가 원자적으로 증감한다.
 * - [resolved]는 "질문자가 충분히 알게 되었다"이며 양방향으로 전이한다.
 */
data class SpotQuestion(
    val id: Long?,
    val catalogId: Long,
    val authorId: Long,
    val body: String,
    val resolved: Boolean,
    val hidden: Boolean,
    val deletedAt: Instant?,
) {
    /** 작성자만 바꿀 수 있다. 이미 그 상태여도 성공이다(멱등). */
    fun resolve(memberId: Long, resolved: Boolean): SpotQuestion {
        requireAuthor(memberId)
        return copy(resolved = resolved)
    }

    /** 삭제(딸린 답변·답글 포함)는 작성자만 할 수 있다. */
    fun requireAuthor(memberId: Long) {
        if (authorId != memberId) throw NotQuestionOwnerException()
    }

    companion object {
        fun write(catalogId: Long, authorId: Long, body: String): SpotQuestion {
            require(catalogId > 0) { "catalogId는 양수여야 합니다." }
            require(authorId > 0) { "authorId는 양수여야 합니다." }
            return SpotQuestion(
                id = null,
                catalogId = catalogId,
                authorId = authorId,
                body = SpotQaText.question(body),
                resolved = false,
                hidden = false,
                deletedAt = null,
            )
        }
    }
}
