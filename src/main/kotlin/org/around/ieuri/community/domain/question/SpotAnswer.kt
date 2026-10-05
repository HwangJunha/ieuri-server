package org.around.ieuri.community.domain.question

import java.time.Instant

/** 질문 애그리게이트의 답변 한 행. [SpotQuestion.answer]로만 만든다. */
data class SpotAnswer(
    val id: Long?,
    val questionId: Long,
    val catalogId: Long,
    val authorId: Long,
    val body: String,
    val hidden: Boolean,
    val deletedAt: Instant?,
)
