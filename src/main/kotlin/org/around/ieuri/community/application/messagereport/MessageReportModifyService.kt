package org.around.ieuri.community.application.messagereport

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.conversation.required.ConversationRepository
import org.around.ieuri.community.application.messagereport.provided.MessageReportCommandUseCase
import org.around.ieuri.community.application.messagereport.provided.dto.ReportMessagesCommand
import org.around.ieuri.community.application.messagereport.required.MessageReportRepository
import org.around.ieuri.community.domain.messagereport.MessageReport
import org.around.ieuri.community.domain.shared.AlreadyReportedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** DM 메시지 다건 신고. 기록만 하며 누적 자동 숨김은 없다(POL-049). 대화 애그리게이트는 읽기만 한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class MessageReportModifyService(
    private val messageReportRepository: MessageReportRepository,
    private val conversationRepository: ConversationRepository,
) : MessageReportCommandUseCase {

    override fun reportMessages(command: ReportMessagesCommand, memberId: Long) {
        val messages = conversationRepository.findMessages(command.messageIds.distinct())
        val conversation = messages.map { it.conversationId }.distinct().singleOrNull()?.let(conversationRepository::findById)

        val report = MessageReport.file(memberId, command.messageIds, messages, conversation, command.reason, command.detail)

        if (!messageReportRepository.insert(report)) throw AlreadyReportedException()
    }
}
