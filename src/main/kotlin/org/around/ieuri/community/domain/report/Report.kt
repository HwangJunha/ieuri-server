package org.around.ieuri.community.domain.report

import org.around.ieuri.community.domain.shared.ReportDetailRequiredException
import org.around.ieuri.community.domain.shared.ReportReason

/**
 * 신고. 애그리게이트 루트이며 생성 뒤 바뀌지 않는다.
 * - 중복 신고는 저장소의 유일 제약 `(reporterId, targetType, targetId)`가 막는다.
 */
data class Report(
    val reporterId: Long,
    val targetType: ReportTargetType,
    val targetId: Long,
    val reason: ReportReason,
    val detail: String?,
) {
    companion object {
        /** 서로 다른 신고자가 이 수에 닿으면 공개 콘텐츠가 숨겨진다. 대상 규모에 따라 낮추지 않는다(POL-041). */
        const val AUTO_HIDE_THRESHOLD = 5

        const val DETAIL_MAX_LENGTH = 500

        fun file(
            reporterId: Long,
            targetType: ReportTargetType,
            targetId: Long,
            reason: ReportReason,
            detail: String?,
        ): Report {
            require(reporterId > 0) { "reporterId는 양수여야 합니다." }
            require(targetId > 0) { "targetId는 양수여야 합니다." }
            val trimmed = detail?.trim()?.takeIf { it.isNotEmpty() }
            if (reason.requiresDetail && trimmed == null) throw ReportDetailRequiredException()
            require(trimmed == null || trimmed.length <= DETAIL_MAX_LENGTH) {
                "상세 사유는 ${DETAIL_MAX_LENGTH}자를 넘을 수 없습니다."
            }
            return Report(reporterId, targetType, targetId, reason, trimmed)
        }

        /** 서로 다른 신고자 수가 임계값에 닿았는지. 자동 숨김 대상이 아닌 종류는 항상 false. */
        fun reachesAutoHideThreshold(targetType: ReportTargetType, distinctReporters: Int): Boolean = targetType.autoHideable && distinctReporters >= AUTO_HIDE_THRESHOLD
    }
}
