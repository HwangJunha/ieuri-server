package org.around.ieuri.community.application.report.provided

import org.around.ieuri.community.domain.report.ReportTargetType
import org.around.ieuri.community.domain.shared.ReportReason

/** 글·댓글·장소 Q&A·회원 신고. DM 메시지 신고는 별도 유스케이스다(POL-049). */
interface ReportCommandUseCase {
    fun report(command: ReportContentCommand, memberId: Long)
}

/** 신고 누적 자동 숨김. 신고가 커밋된 뒤 따로 돈다. */
interface ReportAutoHideUseCase {
    /** 서로 다른 신고자가 임계값에 닿았으면 숨긴다. 이미 숨겨졌으면 아무것도 하지 않는다. */
    fun applyAutoHide(targetType: ReportTargetType, targetId: Long)
}

data class ReportContentCommand(
    val targetType: ReportTargetType,
    val targetId: Long,
    val reason: ReportReason,
    val detail: String?,
) {
    init {
        require(targetId > 0) { "targetId는 양수여야 합니다." }
    }
}
