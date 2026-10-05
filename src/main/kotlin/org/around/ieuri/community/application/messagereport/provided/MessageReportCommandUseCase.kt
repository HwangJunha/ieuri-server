package org.around.ieuri.community.application.messagereport.provided

import org.around.ieuri.community.application.messagereport.provided.dto.ReportMessagesCommand

/** DM 메시지 신고. 여러 건을 한 신고로 접수하며 누적 자동 숨김은 없다(POL-049). */
interface MessageReportCommandUseCase {
    fun reportMessages(command: ReportMessagesCommand, memberId: Long)
}
