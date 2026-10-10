package org.around.ieuri.community.application.question.provided.dto

import java.time.Instant

data class SpotAnswerView(
    val id: Long,
    val questionId: Long,
    val body: String,
    val authorId: Long,
    val ownedByViewer: Boolean,
    /** 이 답변 자체가 숨겨졌는지. 질문이 숨겨져 함께 가려질 때는 바뀌지 않는다(R11). */
    val hidden: Boolean,
    val createdAt: Instant,
)
