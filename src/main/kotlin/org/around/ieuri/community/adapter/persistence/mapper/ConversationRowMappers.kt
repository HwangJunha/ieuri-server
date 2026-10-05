package org.around.ieuri.community.adapter.persistence.mapper

import org.around.ieuri.community.adapter.persistence.table.ConversationTable
import org.around.ieuri.community.adapter.persistence.table.MessageQuoteTable
import org.around.ieuri.community.adapter.persistence.table.MessageTable
import org.around.ieuri.community.domain.conversation.Conversation
import org.around.ieuri.community.domain.conversation.Message
import org.around.ieuri.community.domain.conversation.SpotQaOrigin
import org.around.ieuri.community.domain.shared.PhotoObjectKey
import org.jetbrains.exposed.v1.core.ResultRow

fun ResultRow.toConversation(): Conversation = Conversation(
    id = this[ConversationTable.id],
    memberLow = this[ConversationTable.memberLow],
    memberHigh = this[ConversationTable.memberHigh],
    lastMessageAt = this[ConversationTable.lastMessageAt].toInstant(),
    lastReadMessageIdLow = this[ConversationTable.lastReadMessageIdLow],
    lastReadMessageIdHigh = this[ConversationTable.lastReadMessageIdHigh],
)

/** 메시지 한 행. 복호화한 본문과 인용은 호출자가 넘긴다. 사진 키 파싱 실패는 데이터 손상이라 실패시킨다. */
fun ResultRow.toMessage(decryptedBody: String, spotQaOrigin: SpotQaOrigin?): Message = Message(
    id = this[MessageTable.id],
    conversationId = this[MessageTable.conversationId],
    senderId = this[MessageTable.senderId],
    body = decryptedBody,
    photoKey = this[MessageTable.photoObjectKey]?.let { requireNotNull(PhotoObjectKey.parse(it)) { "적재된 사진 키의 형식이 올바르지 않다." } },
    spotQaOrigin = spotQaOrigin,
    createdAt = this[MessageTable.createdAt].toInstant(),
)

/**
 * 장소 답변 인용 한 행. 복호화한 발췌를 받는다.
 * - title=장소명, primary=질문 발췌, secondary=답변 발췌, context=카탈로그 id, parent=질문 id, target=답변 id.
 */
fun ResultRow.toSpotQaOrigin(decryptedQuestionExcerpt: String, decryptedAnswerExcerpt: String): SpotQaOrigin = SpotQaOrigin(
    catalogId = requireNotNull(this[MessageQuoteTable.contextId]) { "장소 답변 인용에는 카탈로그 id가 있어야 한다." },
    spotTitle = this[MessageQuoteTable.titleSnapshot],
    questionId = requireNotNull(this[MessageQuoteTable.parentId]) { "장소 답변 인용에는 질문 id가 있어야 한다." },
    questionExcerpt = decryptedQuestionExcerpt,
    answerId = this[MessageQuoteTable.targetId],
    answerExcerpt = decryptedAnswerExcerpt,
)
