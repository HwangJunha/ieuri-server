package org.around.ieuri.community.domain.messagereport

/** 신고 대상 메시지들이 속한 대화와 그 참여자. */
data class ReportedConversation(
    val id: Long,
    val participantIds: Set<Long>,
)
