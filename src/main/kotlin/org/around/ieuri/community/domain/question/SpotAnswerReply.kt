package org.around.ieuri.community.domain.question

import java.time.Instant

/** 질문 애그리게이트의 답글 한 행. [SpotQuestion.reply]로만 만든다. */
data class SpotAnswerReply(
    val id: Long?,
    val answerId: Long,
    val catalogId: Long,
    val authorId: Long,
    val body: String,
    val hidden: Boolean,
    val deletedAt: Instant?,
) {
    val kind: SpotQaKind = SpotQaKind.REPLY
}
