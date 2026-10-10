package org.around.ieuri.community.application.conversation.provided.dto

/**
 * 메시지에 붙는 인용 스냅샷(SPEC-0026 R14).
 * - 살아있는 참조가 아니다 — [questionId]·[answerId]로 다시 조회하면 없을 수 있다.
 */
data class ConversationSpotQaOriginView(
    val catalogId: Long,
    val spotTitle: String,
    val questionId: Long,
    val questionBodySnapshot: String,
    val answerId: Long,
    val answerBodySnapshot: String,
)
