package org.around.ieuri.alarm.domain.vo

/**
 * 알림을 눌렀을 때 갈 곳(SPEC-0028 R11).
 *
 * **클라이언트가 추가 조회 없이 라우트를 만들 수 있어야 한다**(AC11.4). 질문 상세 라우트가
 * `spotId`와 `questionId`를 둘 다 경로 파라미터로 요구하므로 Q&A는 둘 다 싣는다 — 하나만
 * 실으면 왕복이 하나 더 생긴다.
 *
 * FCM 데이터 메시지로 나가는 형태는 [asData]가 정한다. GraphQL 계약 표면이 아니다.
 */
sealed interface PushDestination {
    val category: NotificationCategory

    /** FCM 데이터 메시지에 실리는 키-값. 값이 전부 문자열인 것은 FCM 데이터의 제약이다. */
    fun asData(): Map<String, String>

    /** DM 새 메시지 → 대화 화면. */
    data class Dm(val conversationId: Long) : PushDestination {
        override val category = NotificationCategory.MESSAGE

        override fun asData(): Map<String, String> = mapOf(
            KEY_TYPE to TYPE_DM,
            KEY_CONVERSATION_ID to conversationId.toString(),
        )
    }

    /** 내 질문에 달린 답변 → 질문 상세. */
    data class SpotQaAnswer(val spotId: Long, val questionId: Long) : PushDestination {
        override val category = NotificationCategory.SPOT_QA

        override fun asData(): Map<String, String> = mapOf(
            KEY_TYPE to TYPE_SPOT_QA_ANSWER,
            KEY_SPOT_ID to spotId.toString(),
            KEY_QUESTION_ID to questionId.toString(),
        )
    }

    /**
     * 내 답변에 달린 답글 → **그 답변이 속한 질문 상세**(AC7.2).
     * 답글 단독 화면이 없어 목적지가 답변과 같다.
     */
    data class SpotQaReply(val spotId: Long, val questionId: Long) : PushDestination {
        override val category = NotificationCategory.SPOT_QA

        override fun asData(): Map<String, String> = mapOf(
            KEY_TYPE to TYPE_SPOT_QA_REPLY,
            KEY_SPOT_ID to spotId.toString(),
            KEY_QUESTION_ID to questionId.toString(),
        )
    }

    companion object {
        const val KEY_TYPE = "type"
        const val KEY_CONVERSATION_ID = "conversationId"
        const val KEY_SPOT_ID = "spotId"
        const val KEY_QUESTION_ID = "questionId"

        const val TYPE_DM = "DM"
        const val TYPE_SPOT_QA_ANSWER = "SPOT_QA_ANSWER"
        const val TYPE_SPOT_QA_REPLY = "SPOT_QA_REPLY"
    }
}
