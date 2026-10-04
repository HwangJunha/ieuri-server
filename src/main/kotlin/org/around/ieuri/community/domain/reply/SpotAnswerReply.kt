package org.around.ieuri.community.domain.reply

import org.around.ieuri.community.domain.answer.SpotAnswer
import org.around.ieuri.community.domain.shared.SpotQaText
import java.time.Instant

/**
 * 답변에 달린 답글. 애그리게이트 루트이며 답글 한 행만 담는다.
 * - 답글에는 답글을 달 수 없다 — 댓글과 달리 거부한다. [catalogId]는 답변에서 복사한다.
 */
data class SpotAnswerReply(
    val id: Long?,
    val answerId: Long,
    val catalogId: Long,
    val authorId: Long,
    val body: String,
    val hidden: Boolean,
    val deletedAt: Instant?,
) {
    fun requireAuthor(memberId: Long) {
        if (authorId != memberId) throw NotReplyOwnerException()
    }

    companion object {
        fun write(answer: SpotAnswer, authorId: Long, body: String): SpotAnswerReply {
            require(authorId > 0) { "authorId는 양수여야 합니다." }
            return SpotAnswerReply(
                id = null,
                answerId = requireNotNull(answer.id) { "적재된 답변에만 답글을 단다." },
                catalogId = answer.catalogId,
                authorId = authorId,
                body = SpotQaText.answer(body),
                hidden = false,
                deletedAt = null,
            )
        }
    }
}
