package org.around.ieuri.community.domain.question.event

/** 내 질문에 답변이 달림 — 알림 트리거. alarm이 구독한다. 본문을 싣지 않고 id만 싣는다(POL-013). */
data class SpotAnswerWritten(
    /** 질문이 달린 장소. 질문 상세 라우트가 요구한다. */
    val spotId: Long,
    val questionId: Long,
    val answerId: Long,
    /** 답변을 쓴 사람. */
    val actorId: Long,
    /** 질문 작성자 — 알림 수신자다. */
    val recipientId: Long,
)
