package org.around.ieuri.community.application.report

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.comment.provided.CommentNotFoundException
import org.around.ieuri.community.application.exception.AnswerNotFoundException
import org.around.ieuri.community.application.exception.MemberNotFoundException
import org.around.ieuri.community.application.exception.QuestionNotFoundException
import org.around.ieuri.community.application.exception.ReplyNotFoundException
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.around.ieuri.community.application.post.provided.exception.PostNotFoundException
import org.around.ieuri.community.application.report.provided.ReportAutoHideUseCase
import org.around.ieuri.community.application.report.provided.ReportCommandUseCase
import org.around.ieuri.community.application.report.provided.ReportContentCommand
import org.around.ieuri.community.application.report.required.ReportRepository
import org.around.ieuri.community.application.report.required.ReportTargetRepository
import org.around.ieuri.community.domain.report.Report
import org.around.ieuri.community.domain.report.ReportFiled
import org.around.ieuri.community.domain.report.ReportTargetType
import org.around.ieuri.community.domain.shared.AlreadyReportedException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionOperations

/**
 * 신고. 기록만 하고, 자동 숨김은 커밋 뒤 [applyAutoHide]가 신고자 수를 다시 세어 정한다.
 * - 회원 신고는 기록만 한다(POL-041). 회원 존재 확인(auth)은 트랜잭션 밖에서 한다.
 */
@Service
class ReportModifyService(
    private val reportRepository: ReportRepository,
    private val reportTargetRepository: ReportTargetRepository,
    private val memberLookupPort: MemberLookupPort,
    private val eventPublisherPort: CommunityEventPublisherPort,
    @Qualifier(ExposedConfig.EXPOSED_TX_OPERATIONS) private val transaction: TransactionOperations,
) : ReportCommandUseCase,
    ReportAutoHideUseCase {

    override fun report(command: ReportContentCommand, memberId: Long) {
        val report = Report.file(memberId, command.targetType, command.targetId, command.reason, command.detail)
        val isMember = report.targetType == ReportTargetType.MEMBER
        if (isMember && memberLookupPort.findAuthor(report.targetId) == null) throw MemberNotFoundException()

        transaction.executeWithoutResult {
            if (!isMember && !reportTargetRepository.exists(report.targetType, report.targetId)) throw notFound(report.targetType)

            if (!reportRepository.add(report)) throw AlreadyReportedException()

            if (report.targetType.autoHideable) eventPublisherPort.publish(ReportFiled(report.targetType, report.targetId))
        }
    }

    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun applyAutoHide(targetType: ReportTargetType, targetId: Long) {
        val reporters = reportRepository.countDistinctReporters(targetType, targetId)
        if (Report.reachesAutoHideThreshold(targetType, reporters)) reportTargetRepository.hide(targetType, targetId)
    }

    private fun notFound(targetType: ReportTargetType): RuntimeException = when (targetType) {
        ReportTargetType.POST -> PostNotFoundException()
        ReportTargetType.COMMENT -> CommentNotFoundException()
        ReportTargetType.MEMBER -> MemberNotFoundException()
        ReportTargetType.SPOT_QUESTION -> QuestionNotFoundException()
        ReportTargetType.SPOT_ANSWER -> AnswerNotFoundException()
        ReportTargetType.SPOT_ANSWER_REPLY -> ReplyNotFoundException()
    }
}
