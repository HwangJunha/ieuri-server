package org.around.ieuri.community.domain.aggregate

import org.around.ieuri.community.domain.vo.ReportReason
import org.around.ieuri.community.domain.vo.ReportTargetType
import java.time.Instant

/**
 * 신고. 애그리게이트 루트.
 *
 * 같은 대상을 두 번 신고할 수 없다는 규칙은 저장소의 유일 제약 `(reporterId, targetType, targetId)`가
 * 판정한다. 그 제약이 "서로 다른 신고자 5명" 집계의 근거이기도 하다 — 같은 사람이 5번
 * 신고해도 신고자 수는 1이다.
 */
class Report private constructor(
    val id: Long?,
    val reporterId: Long,
    val targetType: ReportTargetType,
    val targetId: Long,
    val reason: ReportReason,
    val detail: String?,
    val createdAt: Instant?,
) {
    companion object {
        /** 자동 숨김 임계값. 서로 다른 신고자가 이 수에 도달하면 글·댓글이 숨겨진다. */
        const val AUTO_HIDE_THRESHOLD = 5

        const val DETAIL_MAX_LENGTH = 500

        fun create(
            reporterId: Long,
            targetType: ReportTargetType,
            targetId: Long,
            reason: ReportReason,
            detail: String?,
        ): Report {
            require(reporterId > 0) { "reporterId는 양수여야 합니다." }
            require(targetId > 0) { "targetId는 양수여야 합니다." }
            val trimmed = detail?.trim()?.takeIf { it.isNotEmpty() }
            require(!(reason.requiresDetail && trimmed == null)) { "상세 사유가 필요합니다." }
            require(trimmed == null || trimmed.length <= DETAIL_MAX_LENGTH) {
                "상세 사유는 ${DETAIL_MAX_LENGTH}자를 넘을 수 없습니다."
            }
            return Report(
                id = null,
                reporterId = reporterId,
                targetType = targetType,
                targetId = targetId,
                reason = reason,
                detail = trimmed,
                createdAt = null,
            )
        }

        /**
         * 신고자 수가 자동 숨김 임계값에 도달했는지. 대상 종류가 자동 숨김 대상이 아니면
         * 아무리 쌓여도 false다.
         */
        fun reachesAutoHideThreshold(targetType: ReportTargetType, distinctReporters: Int): Boolean =
            targetType.autoHideable && distinctReporters >= AUTO_HIDE_THRESHOLD
    }
}
