package org.around.ieuri.community.domain.messagereport

/** 신고 대상 메시지 1건 — 어느 대화에 있고 누가 보냈는가. */
data class ReportedMessage(
    val id: Long,
    val conversationId: Long,
    val senderId: Long,
)
