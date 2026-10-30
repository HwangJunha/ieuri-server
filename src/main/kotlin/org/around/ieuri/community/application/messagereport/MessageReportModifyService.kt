package org.around.ieuri.community.application.messagereport

import org.around.ieuri.community.application.conversation.provided.ConversationQueryUseCase
import org.around.ieuri.community.application.messagereport.provided.MessageReportCommandUseCase
import org.around.ieuri.community.application.messagereport.provided.dto.ReportMessagesCommand
import org.around.ieuri.community.application.messagereport.required.MessageReportRepository
import org.around.ieuri.community.domain.messagereport.MessageReport
import org.around.ieuri.community.domain.messagereport.ReportedConversation
import org.around.ieuri.community.domain.messagereport.ReportedMessage
import org.around.ieuri.community.domain.shared.AlreadyReportedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** DM 메시지 다건 신고. 기록만 하며 누적 자동 숨김은 없다(POL-049). 대화는 조회 유스케이스로 읽기만 한다. */
@Service
@Transactional
class MessageReportModifyService(
    private val messageReportRepository: MessageReportRepository,
    private val conversationQueryUseCase: ConversationQueryUseCase,
) : MessageReportCommandUseCase {

    override fun reportMessages(command: ReportMessagesCommand, memberId: Long) {
        val messages = conversationQueryUseCase.messageOwners(command.messageIds.distinct())
            .map { ReportedMessage(it.messageId, it.conversationId, it.senderId) }
        val conversation = messages.map { it.conversationId }.distinct().singleOrNull()
            ?.let(conversationQueryUseCase::participantsOf)
            ?.let { ReportedConversation(it.conversationId, setOf(it.memberLow, it.memberHigh)) }

        val report = MessageReport.file(memberId, command.messageIds, messages, conversation, command.reason, command.detail)

        if (!messageReportRepository.insert(report)) throw AlreadyReportedException()
    }
}
