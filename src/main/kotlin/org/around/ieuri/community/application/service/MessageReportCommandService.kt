package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.ReportMessagesCommand
import org.around.ieuri.community.application.exception.AlreadyReportedException
import org.around.ieuri.community.application.exception.InvalidInputException
import org.around.ieuri.community.application.exception.NotConversationParticipantException
import org.around.ieuri.community.application.exception.ReportDetailRequiredException
import org.around.ieuri.community.application.port.`in`.command.MessageReportCommandUseCase
import org.around.ieuri.community.application.port.out.persistence.ConversationRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.MessageReportRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.MessageRepositoryPort
import org.around.ieuri.community.domain.aggregate.MessageReport
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * DM 메시지 다건 신고(SPEC-0017 R15).
 *
 * **누적 자동 숨김을 적용하지 않는다**(POL-049). 1:1 대화에는 서로 다른 신고자 5명이라는
 * 개념이 성립하지 않고, 임계값을 1로 낮추면 신고 한 번으로 상대의 발언을 지우는 도구가 된다.
 * 기록만 하고 조치는 사람이 판단한다 — 그래서 [ReportCommandService]와 달리 집계·숨김 경로가
 * 아예 없다.
 *
 * 검증 순서는 **존재 → 소유 → 대화 일관성 → 중복**이다. 앞의 실패를 뒤의 코드로 답하지
 * 않도록 순서를 고정한다.
 */
@Service
class MessageReportCommandService(
    private val messageRepositoryPort: MessageRepositoryPort,
    private val messageReportRepositoryPort: MessageReportRepositoryPort,
    private val conversationRepositoryPort: ConversationRepositoryPort,
) : MessageReportCommandUseCase {

    @Transactional
    override fun reportMessages(command: ReportMessagesCommand, memberId: Long) {
        if (command.reason.requiresDetail && command.detail?.trim().isNullOrEmpty()) {
            throw ReportDetailRequiredException()
        }
        // 빈 목록은 신고가 성립하지 않는다(AC15-3).
        val messageIds = command.messageIds.distinct()
        if (messageIds.isEmpty()) throw InvalidInputException("신고할 메시지를 선택해 주세요.")

        // 1. 존재 — 하나라도 없으면 거부한다. 부분 접수는 없다.
        val messages = messageRepositoryPort.findAllByIds(messageIds)
        if (messages.size != messageIds.size) throw InvalidInputException("신고할 수 없는 메시지가 포함되어 있습니다.")

        // 2. 소유 — 자기 메시지는 신고 대상이 아니다(AC15-5).
        if (messages.any { it.sentBy(memberId) }) {
            throw InvalidInputException("본인이 보낸 메시지는 신고할 수 없습니다.")
        }

        // 3. 대화 일관성 — 한 신고는 한 대화에 대한 것이다(AC15-4). 여러 대화를 섞으면
        //    운영자의 열람 범위가 여러 대화로 넓어진다.
        val conversationIds = messages.map { it.conversationId }.distinct()
        if (conversationIds.size != 1) {
            throw InvalidInputException("한 번에 한 대화의 메시지만 신고할 수 있습니다.")
        }
        val conversationId = conversationIds.single()

        // 참여자가 아닌 대화의 메시지는 신고할 수 없다 — 신고를 남의 대화를 들여다보는
        // 통로로 쓸 수 없어야 한다.
        val conversation = conversationRepositoryPort.findById(conversationId)
            ?: throw InvalidInputException("신고할 수 없는 메시지가 포함되어 있습니다.")
        if (!conversation.hasParticipant(memberId)) throw NotConversationParticipantException()

        // 4. 중복 — 이미 신고한 메시지가 하나라도 있으면 거부한다(AC15-6). 판정은 저장소의
        //    유일 제약이 맡는다.
        val inserted = messageReportRepositoryPort.save(
            MessageReport.create(
                reporterId = memberId,
                conversationId = conversationId,
                messageIds = messageIds,
                reason = command.reason,
                detail = command.detail,
            ),
        )
        if (!inserted) throw AlreadyReportedException()
    }
}
