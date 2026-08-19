package org.around.ieuri.community.domain.event

/**
 * 알림 트리거 3종(SPEC-0028 R5~R7).
 *
 * **`application/event`가 아니라 여기 있는 이유는 구독자가 다른 도메인(alarm)이기 때문이다** —
 * 도메인 간 구독은 `domain/event` 타입으로만 허용된다(LayeredArchitectureTest).
 * community 내부에서만 소비되는 `NewMessageStored`는 계속 `application/event`에 남는다.
 *
 * ### 본문을 싣는 이유
 *
 * 다른 이벤트들이 id만 싣는 것과 다르다. POL-013·POL-050이 막는 것은 **본문을 브로커에
 * 흘리는 것**인데, 이 이벤트는 오늘 Spring ApplicationEvent라 프로세스 밖으로 나가지 않는다.
 * 그리고 이 본문의 목적지는 어차피 FCM이라, DB에서 다시 읽어도 같은 값이 같은 곳으로 간다 —
 * 실어서 늘어나는 노출 범위가 없다.
 *
 * DM 본문은 암호화 저장되므로(POL-050) 다시 읽는 쪽을 택하면 알림 전용 복호화 조회 포트를
 * 새로 만들어야 한다. 그 비용이 이 판단의 실질적인 근거다.
 *
 * **Kafka로 옮길 때 이 결정을 다시 봐야 한다**(POL-028) — 그 시점에는 본문이 브로커를 지나므로
 * id만 싣고 구독자가 되읽는 형태로 바뀌어야 한다.
 */

/** DM 새 메시지 도착(R5). */
data class MessageNotificationTriggered(
    val conversationId: Long,
    val messageId: Long,
    /** 보낸 사람. 수신자와 같을 수 없다. */
    val senderId: Long,
    val recipientId: Long,
    /** 사용자 원문. 사진만 있는 메시지면 비어 있다(AC13.3). */
    val body: String,
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
    val body: String,
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
    val body: String,
)
