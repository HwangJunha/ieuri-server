package org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.FeedItem
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.aggregate.MessageReport
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.FeedItemEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.FeedItemPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageReportEntity

/**
 * 피드·DM 엔티티↔도메인 변환(SPEC-0017). 형태만 옮기며 업무 규칙도 I/O도 없다.
 *
 * **암호화는 파라미터로 받는다**(POL-033) — 매퍼가 키를 들면 순수 함수가 아니게 되고
 * 테스트에 설정이 따라붙는다.
 */

/**
 * 피드 엔티티를 도메인으로. 사진은 별도 테이블이라 **호출자가 읽어 넘긴다**
 * (PostEntity.toDomain과 같은 이유 — 연관으로 두면 조회마다 N+1이 붙는다).
 */
fun FeedItemEntity.toDomain(photoKeys: List<PhotoObjectKey>): FeedItem = FeedItem.restore(
    id = requireNotNull(id) { "적재된 피드에는 id가 있어야 한다." },
    spotId = spotId,
    authorId = authorId,
    body = body,
    photoKeys = photoKeys,
    likeCount = likeCount,
    hidden = hidden,
    deletedAt = deletedAt,
    createdAt = requireNotNull(createdAt) { "적재된 피드에는 createdAt이 있어야 한다." },
)

/** 도메인 피드를 새 엔티티로. id·시각은 DB가 채우고, 사진은 id가 정해진 뒤 따로 적재한다. */
fun FeedItem.toNewEntity(): FeedItemEntity = FeedItemEntity(
    authorId = authorId,
    spotId = spotId,
    body = body,
    likeCount = likeCount,
    hidden = hidden,
    deletedAt = deletedAt,
)

/** 배열 순서가 표시 순서다 — position에 그대로 싣는다. */
fun FeedItem.toPhotoEntities(feedItemId: Long): List<FeedItemPhotoEntity> =
    photoKeys.mapIndexed { index, key ->
        FeedItemPhotoEntity(feedItemId = feedItemId, objectKey = key.value, position = index.toShort())
    }

/**
 * 적재 시점에 형식을 통과한 값이므로 파싱 실패는 데이터 손상이다 — **조용히 버리지 않고
 * 실패시킨다**. 그냥 넘기면 피드에서 사진 한 장이 이유 없이 사라진다.
 */
fun FeedItemPhotoEntity.toPhotoKey(): PhotoObjectKey =
    requireNotNull(PhotoObjectKey.parse(objectKey)) {
        "적재된 사진 키(${objectKey})의 형식이 올바르지 않다."
    }

fun ConversationEntity.toDomain(): Conversation = Conversation.restore(
    id = requireNotNull(id) { "적재된 대화에는 id가 있어야 한다." },
    memberLow = memberLow,
    memberHigh = memberHigh,
    lastMessageAt = requireNotNull(lastMessageAt) { "적재된 대화에는 lastMessageAt이 있어야 한다." },
    createdAt = requireNotNull(createdAt) { "적재된 대화에는 createdAt이 있어야 한다." },
)

fun Conversation.toNewEntity(): ConversationEntity = ConversationEntity(
    memberLow = memberLow,
    memberHigh = memberHigh,
    lastMessageAt = lastMessageAt,
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
    feedItemId = feedItemId,
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
    feedItemId = feedItemId,
    photoObjectKey = photoKey?.value,
)

fun MessageReport.toNewEntity(): MessageReportEntity = MessageReportEntity(
    reporterId = reporterId,
    conversationId = conversationId,
    reason = reason,
    detail = detail,
)
