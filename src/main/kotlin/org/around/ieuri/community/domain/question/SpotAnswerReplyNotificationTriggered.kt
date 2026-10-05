package org.around.ieuri.community.domain.question

/** 내 답변에 답글이 달림 — 알림 트리거. 목적지는 그 답변이 속한 질문 상세다. 본문을 싣지 않고 id만 싣는다(POL-013). */
data class SpotAnswerReplyNotificationTriggered(
    val spotId: Long,
    val questionId: Long,
    val replyId: Long,
    /** 답글을 쓴 사람. */
    val actorId: Long,
    /** 답변 작성자 — 알림 수신자다. 질문 작성자에게는 보내지 않는다. */
    val recipientId: Long,
)
