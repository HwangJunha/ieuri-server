package org.around.ieuri.alarm.domain.push

import org.around.ieuri.alarm.domain.preference.NotificationCategory

/**
 * 알림을 눌렀을 때 갈 곳(SPEC-0028 R11). 클라이언트가 추가 조회 없이 라우트를 만든다(AC11.4).
 * - FCM 데이터 메시지 형태는 [asData]가 정한다.
 */
sealed interface PushDestination {
    /** 사용자가 끄고 켜는 단위(R10). 답변과 답글은 같은 카테고리다. */
    val category: NotificationCategory

    /** 문구 조회 키이자 FCM 데이터의 `type` 값. 답변과 답글이 갈린다. */
    val type: NotificationType

    /** FCM 데이터 메시지에 실리는 키-값. FCM 제약으로 값은 전부 문자열이다. */
    fun asData(): Map<String, String>

    /** DM 새 메시지 → 대화 화면. */
    data class Dm(val conversationId: Long) : PushDestination {
        override val category = NotificationCategory.MESSAGE
        override val type = NotificationType.DM

        override fun asData(): Map<String, String> = mapOf(
            KEY_TYPE to type.name,
            KEY_CONVERSATION_ID to conversationId.toString(),
        )
    }

    /** 내 질문에 달린 답변 → 질문 상세. */
    data class SpotQaAnswer(val spotId: Long, val questionId: Long) : PushDestination {
        override val category = NotificationCategory.SPOT_QA
        override val type = NotificationType.SPOT_QA_ANSWER

        override fun asData(): Map<String, String> = mapOf(
            KEY_TYPE to type.name,
            KEY_SPOT_ID to spotId.toString(),
            KEY_QUESTION_ID to questionId.toString(),
        )
    }

    /** 내 답변에 달린 답글 → 그 답변이 속한 질문 상세(AC7.2). */
    data class SpotQaReply(val spotId: Long, val questionId: Long) : PushDestination {
        override val category = NotificationCategory.SPOT_QA
        override val type = NotificationType.SPOT_QA_REPLY

        override fun asData(): Map<String, String> = mapOf(
            KEY_TYPE to type.name,
            KEY_SPOT_ID to spotId.toString(),
            KEY_QUESTION_ID to questionId.toString(),
        )
    }

    companion object {
        const val KEY_TYPE = "type"
        const val KEY_CONVERSATION_ID = "conversationId"
        const val KEY_SPOT_ID = "spotId"
        const val KEY_QUESTION_ID = "questionId"
    }
}
