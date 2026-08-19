package org.around.ieuri.community.domain.event

/**
 * 알림 트리거 3종(SPEC-0028 R5~R7).
 *
 * **`application/event`가 아니라 여기 있는 이유는 구독자가 다른 도메인(alarm)이기 때문이다** —
 * 도메인 간 구독은 `domain/event` 타입으로만 허용된다(LayeredArchitectureTest).
 * community 내부에서만 소비되는 `NewMessageStored`는 계속 `application/event`에 남는다.
 *
 * **본문을 싣지 않는다.** 푸시가 제목만 보내고 사용자가 쓴 내용은 담지 않으므로(R13),
 * 알림 경로에 본문이 필요한 지점이 아예 없다. 다른 이벤트들과 같이 id만 싣는다(POL-013).
 */

/** DM 새 메시지 도착(R5). */
data class MessageNotificationTriggered(
    val conversationId: Long,
    val messageId: Long,
    /** 보낸 사람. 수신자와 같을 수 없다. */
    val senderId: Long,
    val recipientId: Long,
)

/** 내 질문에 답변이 달림(R6). */
data class SpotAnswerNotificationTriggered(
    /** 질문이 달린 장소. 질문 상세 라우트가 요구한다(AC6.2). */
    val spotId: Long,
    val questionId: Long,
    val answerId: Long,
    /** 답변을 쓴 사람. */
    val actorId: Long,
    /** 질문 작성자 — 알림 수신자다. */
    val recipientId: Long,
)

/** 내 답변에 답글이 달림(R7). 목적지는 그 답변이 속한 질문 상세다(AC7.2). */
data class SpotAnswerReplyNotificationTriggered(
    val spotId: Long,
    val questionId: Long,
    val replyId: Long,
    /** 답글을 쓴 사람. */
    val actorId: Long,
    /** 답변 작성자 — 알림 수신자다. 질문 작성자에게는 보내지 않는다(AC7.3). */
    val recipientId: Long,
)
