package org.around.ieuri.community.domain.aggregate

import org.around.ieuri.community.domain.vo.ReportReason
import java.time.Instant

/**
 * DM 메시지 신고. 애그리게이트 루트. **여러 메시지를 한 건으로 접수한다**(R15).
 *
 * [Report]와 나누는 이유는 다건이기 때문이다 — targetId가 단수인 그쪽에 억지로 끼우면
 * 같은 신고가 N번 접수되어 운영자에게 한 사건이 N건으로 보인다.
 *
 * **누적 자동 숨김이 없다**(POL-049). 1:1 대화에는 서로 다른 신고자 5명이라는 개념이
 * 성립하지 않고, 임계값을 1로 낮추면 신고 한 번으로 상대를 침묵시키는 도구가 된다.
 * 신고는 기록하고 조치는 사람이 판단한다.
 *
 * [messageIds]가 **운영자의 열람 범위**이기도 하다 — 전후 맥락도, 대화 전체도 열지 않는다.
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
         * 새 신고. **대상이 비어 있으면 거부한다**(AC15-3) — 신고할 것이 없는 신고는 없다.
         *
         * 같은 메시지를 두 번 담아도 하나로 본다. 대화 일관성·소유·존재·중복 판정은
         * 저장소를 물어야 하므로 애플리케이션 계층의 책임이다.
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
