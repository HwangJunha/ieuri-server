package org.around.ieuri.community.application.report.provided

import org.around.ieuri.community.application.report.provided.dto.ReportContentCommand

/** 글·댓글·장소 Q&A·회원 신고. DM 메시지 신고는 별도 유스케이스다(POL-049). */
interface ReportCommandUseCase {
    fun report(command: ReportContentCommand, memberId: Long)
}
