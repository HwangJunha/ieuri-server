package org.around.ieuri.community.application.messagereport.provided.dto

import org.around.ieuri.community.domain.shared.ReportReason

/** 메시지 다건 신고. 빈 목록·대화 혼재 판정은 도메인이 한다. */
data class ReportMessagesCommand(
    val messageIds: List<Long>,
    val reason: ReportReason,
    val detail: String?,
)
