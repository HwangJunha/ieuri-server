package org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.aggregate.MessageReport
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.SpotQaOrigin
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageReportEntity

/**
 * DM 엔티티↔도메인 변환(SPEC-0017). 형태만 옮기며 업무 규칙도 I/O도 없다.
 *
 * **암호화는 파라미터로 받는다**(POL-033) — 매퍼가 키를 들면 순수 함수가 아니게 되고
 * 테스트에 설정이 따라붙는다.
 */

/**
 * 대화 엔티티를 도메인으로.
 *
 * **출처 발췌의 평문을 받는다**(POL-033) — 복호화는 키를 아는 어댑터가 하고 매퍼는 순수
 * 함수로 남는다. 출처가 없는 일반 DM이면 두 인자가 null이며, 그때 [Conversation.spotQaOrigin]도
 * null이다.
 */
fun ConversationEntity.toDomain(
    decryptedQuestionExcerpt: String? = null,
    decryptedAnswerExcerpt: String? = null,
): Conversation = Conversation.restore(
    id = requireNotNull(id) { "적재된 대화에는 id가 있어야 한다." },
    memberLow = memberLow,
    memberHigh = memberHigh,
    lastMessageAt = requireNotNull(lastMessageAt) { "적재된 대화에는 lastMessageAt이 있어야 한다." },
    createdAt = requireNotNull(createdAt) { "적재된 대화에는 createdAt이 있어야 한다." },
    lastReadMessageIdLow = lastReadMessageIdLow,
    lastReadMessageIdHigh = lastReadMessageIdHigh,
    spotQaOrigin = toSpotQaOrigin(decryptedQuestionExcerpt, decryptedAnswerExcerpt),
)

/**
 * 출처 스냅샷. **여섯 값이 전부 있어야 성립한다** — DB의 CHECK가 그 상태를 강제하므로
 * 하나라도 비면 출처가 없는 것으로 읽는다.
 */
private fun ConversationEntity.toSpotQaOrigin(
    decryptedQuestionExcerpt: String?,
    decryptedAnswerExcerpt: String?,
): SpotQaOrigin? = SpotQaOrigin(
    catalogId = spotQaCatalogId ?: return null,
    spotTitle = spotQaSpotTitle ?: return null,
    questionId = spotQaQuestionId ?: return null,
    questionExcerpt = decryptedQuestionExcerpt ?: return null,
    answerId = spotQaAnswerId ?: return null,
    answerExcerpt = decryptedAnswerExcerpt ?: return null,
)

/**
 * 도메인 대화를 새 엔티티로. **발췌의 암호문을 받는다** — 평문이 이 컬럼에 들어가는 경로는 없다.
 * 출처가 없으면 두 인자가 null이고 여섯 컬럼이 전부 비어 CHECK를 만족한다.
 */
fun Conversation.toNewEntity(
    encryptedQuestionExcerpt: String? = null,
    encryptedAnswerExcerpt: String? = null,
): ConversationEntity = ConversationEntity(
    memberLow = memberLow,
    memberHigh = memberHigh,
    lastMessageAt = lastMessageAt,
    lastReadMessageIdLow = lastReadMessageIdLow,
    lastReadMessageIdHigh = lastReadMessageIdHigh,
    spotQaCatalogId = spotQaOrigin?.catalogId,
    spotQaSpotTitle = spotQaOrigin?.spotTitle,
    spotQaQuestionId = spotQaOrigin?.questionId,
    spotQaQuestionBodyEncrypted = spotQaOrigin?.let { encryptedQuestionExcerpt },
    spotQaAnswerId = spotQaOrigin?.answerId,
    spotQaAnswerBodyEncrypted = spotQaOrigin?.let { encryptedAnswerExcerpt },
)

/**
 * 메시지 엔티티를 도메인으로. **[decryptedBody]를 받는다** — 복호화는 키를 아는 어댑터가
 * 하고 매퍼는 순수 함수로 남는다(POL-033).
 */
fun MessageEntity.toDomain(decryptedBody: String): Message = Message.restore(
    id = requireNotNull(id) { "적재된 메시지에는 id가 있어야 한다." },
    conversationId = conversationId,
    senderId = senderId,
    body = decryptedBody,
    createdAt = requireNotNull(createdAt) { "적재된 메시지에는 createdAt이 있어야 한다." },
    // 적재 시점에 형식을 통과한 값이라 파싱 실패는 데이터 손상이다 — 조용히 버리지 않는다.
    photoKey = photoObjectKey?.let {
        requireNotNull(PhotoObjectKey.parse(it)) { "적재된 사진 키($it)의 형식이 올바르지 않다." }
    },
)

/** **[encryptedBody]를 받는다** — 평문이 이 컬럼에 들어가는 경로를 만들지 않는다(R16). */
fun Message.toNewEntity(encryptedBody: String): MessageEntity = MessageEntity(
    conversationId = conversationId,
    senderId = senderId,
    bodyEncrypted = encryptedBody,
    photoObjectKey = photoKey?.value,
)

fun MessageReport.toNewEntity(): MessageReportEntity = MessageReportEntity(
    reporterId = reporterId,
    conversationId = conversationId,
    reason = reason,
    detail = detail,
)
