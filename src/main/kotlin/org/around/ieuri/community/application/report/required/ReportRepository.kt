package org.around.ieuri.community.application.report.required

import org.around.ieuri.community.domain.report.Report
import org.around.ieuri.community.domain.report.ReportTargetType

interface ReportRepository {
    /** 새로 기록했으면 true, 같은 대상을 이미 신고했으면 false. */
    fun add(report: Report): Boolean

    /** 대상을 신고한 서로 다른 신고자 수. */
    fun countDistinctReporters(targetType: ReportTargetType, targetId: Long): Int
}
