package org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.aggregate.MessageReport
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.SpotQaOrigin
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageQuoteEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageQuoteType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageReportEntity

/**
 * DM 엔티티↔도메인 변환(SPEC-0017). 형태만 옮기며 업무 규칙도 I/O도 없다.
 *
 * **암호화는 파라미터로 받는다**(POL-033) — 매퍼가 키를 들면 순수 함수가 아니게 되고
 * 테스트에 설정이 따라붙는다.
 */

fun ConversationEntity.toDomain(): Conversation = Conversation.restore(
    id = requireNotNull(id) { "적재된 대화에는 id가 있어야 한다." },
    memberLow = memberLow,
    memberHigh = memberHigh,
    lastMessageAt = requireNotNull(lastMessageAt) { "적재된 대화에는 lastMessageAt이 있어야 한다." },
    createdAt = requireNotNull(createdAt) { "적재된 대화에는 createdAt이 있어야 한다." },
    lastReadMessageIdLow = lastReadMessageIdLow,
    lastReadMessageIdHigh = lastReadMessageIdHigh,
)

fun Conversation.toNewEntity(): ConversationEntity = ConversationEntity(
    memberLow = memberLow,
    memberHigh = memberHigh,
    lastMessageAt = lastMessageAt,
    lastReadMessageIdLow = lastReadMessageIdLow,
    lastReadMessageIdHigh = lastReadMessageIdHigh,
)

/**
 * 메시지 엔티티를 도메인으로. **평문을 파라미터로 받는다** — 복호화는 키를 아는 어댑터가
 * 하고 매퍼는 순수 함수로 남는다(POL-033). 인용은 별도 표라 호출자가 읽어 넘긴다.
 */
fun MessageEntity.toDomain(
    decryptedBody: String,
    spotQaOrigin: SpotQaOrigin? = null,
): Message = Message.restore(
    id = requireNotNull(id) { "적재된 메시지에는 id가 있어야 한다." },
    conversationId = conversationId,
    senderId = senderId,
    body = decryptedBody,
    createdAt = requireNotNull(createdAt) { "적재된 메시지에는 createdAt이 있어야 한다." },
    // 적재 시점에 형식을 통과한 값이라 파싱 실패는 데이터 손상이다 — 조용히 버리지 않는다.
    photoKey = photoObjectKey?.let {
        requireNotNull(PhotoObjectKey.parse(it)) { "적재된 사진 키($it)의 형식이 올바르지 않다." }
    },
    spotQaOrigin = spotQaOrigin,
)

/** **[encryptedBody]를 받는다** — 평문이 이 컬럼에 들어가는 경로를 만들지 않는다(R16). */
fun Message.toNewEntity(encryptedBody: String): MessageEntity = MessageEntity(
    conversationId = conversationId,
    senderId = senderId,
    bodyEncrypted = encryptedBody,
    photoObjectKey = photoKey?.value,
)

/**
 * 인용 엔티티를 도메인 값으로. **발췌 평문을 파라미터로 받는다**(POL-033).
 *
 * 장소 답변 인용의 자리 배치: title=장소명, primary=질문 발췌, secondary=답변 발췌,
 * context=카탈로그 id, parent=질문 id, target=답변 id. 종류가 늘면 그 종류의 배치를
 * 여기 분기로 더한다.
 */
fun MessageQuoteEntity.toSpotQaOrigin(
    decryptedPrimaryExcerpt: String,
    decryptedSecondaryExcerpt: String,
): SpotQaOrigin = SpotQaOrigin(
    catalogId = requireNotNull(contextId) { "장소 답변 인용에는 카탈로그 id가 있어야 한다." },
    spotTitle = titleSnapshot,
    questionId = requireNotNull(parentId) { "장소 답변 인용에는 질문 id가 있어야 한다." },
    questionExcerpt = decryptedPrimaryExcerpt,
    answerId = targetId,
    answerExcerpt = decryptedSecondaryExcerpt,
)

/** **암호문을 받는다** — 평문이 이 컬럼들에 들어가는 경로를 만들지 않는다(POL-050). */
fun SpotQaOrigin.toQuoteEntity(
    messageId: Long,
    encryptedQuestionExcerpt: String,
    encryptedAnswerExcerpt: String,
): MessageQuoteEntity = MessageQuoteEntity(
    messageId = messageId,
    quoteType = MessageQuoteType.SPOT_ANSWER,
    targetId = answerId,
    titleSnapshot = spotTitle,
    primaryExcerptEncrypted = encryptedQuestionExcerpt,
    secondaryExcerptEncrypted = encryptedAnswerExcerpt,
    contextId = catalogId,
    parentId = questionId,
)

fun MessageReport.toNewEntity(): MessageReportEntity = MessageReportEntity(
    reporterId = reporterId,
    conversationId = conversationId,
    reason = reason,
    detail = detail,
)
