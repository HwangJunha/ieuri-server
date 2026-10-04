package org.around.ieuri.community.domain.aggregate

import org.around.ieuri.community.domain.shared.ReportReason
import java.time.Instant

/**
 * DM 메시지 신고. 애그리게이트 루트. 여러 메시지를 한 건으로 접수한다(R15).
 * - 누적 자동 숨김이 없다(POL-049). 조치는 사람이 판단한다.
 * - [messageIds]가 운영자의 열람 범위다. 전후 맥락이나 대화 전체는 열지 않는다.
 */
class MessageReport private constructor(
    val id: Long?,
    val reporterId: Long,
    /** 한 신고는 한 대화에 대한 것이다. 여러 대화의 메시지를 섞을 수 없다(AC15-4). */
    val conversationId: Long,
    val messageIds: List<Long>,
    val reason: ReportReason,
    val detail: String?,
    val createdAt: Instant?,
) {
    companion object {
        const val DETAIL_MAX_LENGTH = 500

        /**
         * 새 신고. 대상이 비어 있으면 거부하고(AC15-3), 같은 메시지 중복은 하나로 본다.
         * - 대화 일관성·소유·존재·중복 신고 판정은 애플리케이션 계층이 한다.
         */
        fun create(
            reporterId: Long,
            conversationId: Long,
            messageIds: List<Long>,
            reason: ReportReason,
            detail: String?,
        ): MessageReport {
            require(reporterId > 0) { "reporterId는 양수여야 합니다." }
            require(conversationId > 0) { "conversationId는 양수여야 합니다." }
            require(messageIds.isNotEmpty()) { "신고할 메시지를 선택해 주세요." }
            require(messageIds.all { it > 0 }) { "messageId는 양수여야 합니다." }
            val trimmed = detail?.trim()?.takeIf { it.isNotEmpty() }
            require(!(reason.requiresDetail && trimmed == null)) { "상세 사유가 필요합니다." }
            require(trimmed == null || trimmed.length <= DETAIL_MAX_LENGTH) {
                "상세 사유는 ${DETAIL_MAX_LENGTH}자를 넘을 수 없습니다."
            }
            return MessageReport(
                id = null,
                reporterId = reporterId,
                conversationId = conversationId,
                messageIds = messageIds.distinct(),
                reason = reason,
                detail = trimmed,
                createdAt = null,
            )
        }
    }
}
