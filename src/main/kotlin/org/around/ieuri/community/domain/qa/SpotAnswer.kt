package org.around.ieuri.community.domain.qa

import java.time.Instant

/**
 * 질문에 달린 답변. 애그리게이트 루트이며 답변 한 행만 담는다.
 * - [catalogId]는 질문에서 복사하며 갱신되지 않는다. 해결된 질문에도 답할 수 있다.
 */
data class SpotAnswer(
    val id: Long?,
    val questionId: Long,
    val catalogId: Long,
    val authorId: Long,
    val body: String,
    val hidden: Boolean,
    val deletedAt: Instant?,
) {
    /** 삭제(딸린 답글 포함)는 작성자만 할 수 있다. */
    fun requireAuthor(memberId: Long) {
        if (authorId != memberId) throw NotAnswerOwnerException()
    }

    companion object {
        fun write(question: SpotQuestion, authorId: Long, body: String): SpotAnswer {
            require(authorId > 0) { "authorId는 양수여야 합니다." }
            return SpotAnswer(
                id = null,
                questionId = requireNotNull(question.id) { "적재된 질문에만 답한다." },
                catalogId = question.catalogId,
                authorId = authorId,
                body = SpotQaText.answer(body),
                hidden = false,
                deletedAt = null,
            )
        }
    }
}
