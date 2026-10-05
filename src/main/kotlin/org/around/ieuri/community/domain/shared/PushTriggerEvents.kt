package org.around.ieuri.community.domain.shared

/*
 * 알림 트리거 3종(SPEC-0028 R5~R7). alarm 도메인이 구독한다.
 * - 본문을 싣지 않고 id만 싣는다(R13, POL-013).
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
