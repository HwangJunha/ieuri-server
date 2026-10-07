package org.around.ieuri.community.application.question.provided.dto

import java.time.Instant

data class SpotQuestionView(
    val id: Long,
    /** 이 질문이 달린 장소. 언어와 무관한 카탈로그 id다. */
    val catalogId: Long,
    val body: String,
    val authorId: Long,
    val resolved: Boolean,
    /** 숨겨진 답변을 제외한 수. 모든 호출자에게 같은 값이다. */
    val answerCount: Int,
    val ownedByViewer: Boolean,
    val hidden: Boolean,
    val createdAt: Instant,
)
