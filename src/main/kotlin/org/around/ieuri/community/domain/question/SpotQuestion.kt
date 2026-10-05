package org.around.ieuri.community.domain.question

import java.time.Instant

/**
 * 장소에 달린 질문. 답변·답글을 품는 애그리게이트의 루트이며, 답변·답글은 이 루트를 통해서만 만들고 지운다.
 * - 루트는 질문 한 행만 담는다. 자식은 필요한 한 행만 따로 읽는다 — 다른 답변을 읽지 않는다.
 * - 답변 수는 상태가 아니다 — 저장소가 원자적으로 증감한다.
 * - [resolved]는 "질문자가 충분히 알게 되었다"이며 양방향으로 전이한다. 해결된 질문에도 답할 수 있다.
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

    /** [catalogId]는 질문에서 복사하며 갱신되지 않는다. */
    fun answer(authorId: Long, body: String): SpotAnswer {
        require(authorId > 0) { "authorId는 양수여야 합니다." }
        return SpotAnswer(
            id = null,
            questionId = requireLoadedId(),
            catalogId = catalogId,
            authorId = authorId,
            body = SpotQaText.answer(body),
            hidden = false,
            deletedAt = null,
        )
    }

    /** 이 질문의 답변에만 답글을 단다. 답글에는 답글을 달 수 없다. */
    fun reply(answer: SpotAnswer, authorId: Long, body: String): SpotAnswerReply {
        requireOwn(answer)
        require(authorId > 0) { "authorId는 양수여야 합니다." }
        return SpotAnswerReply(
            id = null,
            answerId = requireNotNull(answer.id) { "적재된 답변에만 답글을 단다." },
            catalogId = catalogId,
            authorId = authorId,
            body = SpotQaText.answer(body),
            hidden = false,
            deletedAt = null,
        )
    }

    /** 답변 삭제(딸린 답글 포함)는 답변 작성자만 할 수 있다. */
    fun removeAnswer(answer: SpotAnswer, memberId: Long) {
        requireOwn(answer)
        if (answer.authorId != memberId) throw NotAnswerOwnerException()
    }

    /** [reply]는 이 질문의 답변에 달린 것이어야 한다 — 저장소가 답변 id로 이 질문을 찾아 보장한다. */
    fun removeReply(reply: SpotAnswerReply, memberId: Long) {
        if (reply.authorId != memberId) throw NotReplyOwnerException()
    }

    private fun requireOwn(answer: SpotAnswer) {
        require(answer.questionId == requireLoadedId()) { "다른 질문의 답변이다." }
    }

    private fun requireLoadedId(): Long = requireNotNull(id) { "적재된 질문에만 답한다." }

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
