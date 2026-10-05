package org.around.ieuri.community.domain.messagereport

import org.around.ieuri.community.domain.conversation.Conversation
import org.around.ieuri.community.domain.conversation.Message
import org.around.ieuri.community.domain.shared.InvalidInputException
import org.around.ieuri.community.domain.shared.ReportDetailRequiredException
import org.around.ieuri.community.domain.shared.ReportReason

/**
 * DM 메시지 신고. 여러 메시지를 한 건으로 접수한다. 누적 자동 숨김이 없다(POL-049) — 조치는 사람이 판단한다.
 * - [messageIds]가 운영자의 열람 범위다. 한 신고는 한 대화에 대한 것이다.
 */
data class MessageReport(
    val id: Long?,
    val reporterId: Long,
    val conversationId: Long,
    val messageIds: List<Long>,
    val reason: ReportReason,
    val detail: String?,
) {
    companion object {
        const val DETAIL_MAX_LENGTH = 500

        private fun requireDetail(reason: ReportReason, detail: String?): String? {
            val trimmed = detail?.trim()?.takeIf { it.isNotEmpty() }
            if (reason.requiresDetail && trimmed == null) throw ReportDetailRequiredException()
            require(trimmed == null || trimmed.length <= DETAIL_MAX_LENGTH) { "상세 사유는 ${DETAIL_MAX_LENGTH}자를 넘을 수 없습니다." }
            return trimmed
        }

        /**
         * 판정 순서는 상세 사유 → 존재 → 소유 → 대화 일관성 → 참여다. 메시지 id 중복은 하나로 보고, 부분 접수는 없다.
         * - [messages]는 [messageIds] 중 살아 있는 것, [conversation]은 그 메시지들의 대화다(없으면 null).
         */
        fun file(
            reporterId: Long,
            messageIds: List<Long>,
            messages: List<Message>,
            conversation: Conversation?,
            reason: ReportReason,
            detail: String?,
        ): MessageReport {
            val trimmed = requireDetail(reason, detail)
            val ids = messageIds.distinct()
            if (ids.isEmpty()) throw InvalidInputException("신고할 메시지를 선택해 주세요.")
            if (messages.size != ids.size) throw InvalidInputException("신고할 수 없는 메시지가 포함되어 있습니다.")
            if (messages.any { it.sentBy(reporterId) }) throw InvalidInputException("본인이 보낸 메시지는 신고할 수 없습니다.")
            if (messages.map { it.conversationId }.distinct().size != 1) throw InvalidInputException("한 번에 한 대화의 메시지만 신고할 수 있습니다.")
            if (conversation == null) throw InvalidInputException("신고할 수 없는 메시지가 포함되어 있습니다.")
            conversation.requireParticipant(reporterId)
            return MessageReport(
                id = null,
                reporterId = reporterId,
                conversationId = requireNotNull(conversation.id),
                messageIds = ids,
                reason = reason,
                detail = trimmed,
            )
        }
    }
}
