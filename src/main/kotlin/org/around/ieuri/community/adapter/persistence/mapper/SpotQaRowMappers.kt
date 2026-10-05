package org.around.ieuri.community.adapter.persistence.mapper

import org.around.ieuri.community.adapter.persistence.table.SpotAnswerReplyTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerTable
import org.around.ieuri.community.adapter.persistence.table.SpotQuestionTable
import org.around.ieuri.community.domain.qa.SpotAnswer
import org.around.ieuri.community.domain.qa.SpotAnswerReply
import org.around.ieuri.community.domain.qa.SpotQuestion
import org.jetbrains.exposed.v1.core.ResultRow

/** 질문 한 행. 사진은 읽기 쪽 필드 리졸버가 따로 가져온다. */
fun ResultRow.toSpotQuestion(): SpotQuestion = SpotQuestion(
    id = this[SpotQuestionTable.id],
    catalogId = this[SpotQuestionTable.catalogId],
    authorId = this[SpotQuestionTable.authorId],
    body = this[SpotQuestionTable.body],
    resolved = this[SpotQuestionTable.resolved],
    hidden = this[SpotQuestionTable.hidden],
    deletedAt = this[SpotQuestionTable.deletedAt]?.toInstant(),
)

fun ResultRow.toSpotAnswer(): SpotAnswer = SpotAnswer(
    id = this[SpotAnswerTable.id],
    questionId = this[SpotAnswerTable.questionId],
    catalogId = this[SpotAnswerTable.catalogId],
    authorId = this[SpotAnswerTable.authorId],
    body = this[SpotAnswerTable.body],
    hidden = this[SpotAnswerTable.hidden],
    deletedAt = this[SpotAnswerTable.deletedAt]?.toInstant(),
)

fun ResultRow.toSpotAnswerReply(): SpotAnswerReply = SpotAnswerReply(
    id = this[SpotAnswerReplyTable.id],
    answerId = this[SpotAnswerReplyTable.answerId],
    catalogId = this[SpotAnswerReplyTable.catalogId],
    authorId = this[SpotAnswerReplyTable.authorId],
    body = this[SpotAnswerReplyTable.body],
    hidden = this[SpotAnswerReplyTable.hidden],
    deletedAt = this[SpotAnswerReplyTable.deletedAt]?.toInstant(),
)
