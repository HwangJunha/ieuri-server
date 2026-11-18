package org.around.ieuri.community.application.report.provided.dto

import org.around.ieuri.community.domain.report.ReportTargetType
import org.around.ieuri.community.domain.shared.ReportReason

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
