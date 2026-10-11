package org.around.ieuri.community.application.question.provided.dto

/** 답변과 그 답변이 달린 질문의 원문. DM에서 답변을 인용할 때 쓴다. */
data class SpotAnswerOriginView(
    val answerId: Long,
    val answerAuthorId: Long,
    val answerBody: String,
    val questionId: Long,
    val questionBody: String,
    val catalogId: Long,
)
