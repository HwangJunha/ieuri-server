package org.around.ieuri.community.application.report

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.comment.provided.CommentNotFoundException
import org.around.ieuri.community.application.comment.required.CommentRepository
import org.around.ieuri.community.application.exception.AnswerNotFoundException
import org.around.ieuri.community.application.exception.MemberNotFoundException
import org.around.ieuri.community.application.exception.QuestionNotFoundException
import org.around.ieuri.community.application.exception.ReplyNotFoundException
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.around.ieuri.community.application.port.out.persistence.SpotQaModerationPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionCounterPort
import org.around.ieuri.community.application.post.provided.exception.PostNotFoundException
import org.around.ieuri.community.application.post.required.PostRepository
import org.around.ieuri.community.application.report.provided.ReportAutoHideUseCase
import org.around.ieuri.community.application.report.provided.ReportCommandUseCase
import org.around.ieuri.community.application.report.provided.ReportContentCommand
import org.around.ieuri.community.application.report.required.ReportRepository
import org.around.ieuri.community.domain.report.Report
import org.around.ieuri.community.domain.report.ReportFiled
import org.around.ieuri.community.domain.report.ReportTargetType
import org.around.ieuri.community.domain.shared.AlreadyReportedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 신고. 기록만 하고, 자동 숨김은 커밋 뒤 [applyAutoHide]가 신고자 수를 다시 세어 정한다.
 * - 회원 신고는 기록만 한다(POL-041). 회원 존재 확인(auth)은 첫 쿼리 전에 한다.
 * - 장소 Q&A 대상은 Q&A 이전 전까지 옛 포트([SpotQaModerationPort])로 확인·숨긴다.
 */
@Service
class ReportModifyService(
    private val reportRepository: ReportRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val spotQaModerationPort: SpotQaModerationPort,
    private val spotQuestionCounterPort: SpotQuestionCounterPort,
    private val memberLookupPort: MemberLookupPort,
    private val eventPublisherPort: CommunityEventPublisherPort,
) : ReportCommandUseCase,
    ReportAutoHideUseCase {

    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
    override fun report(command: ReportContentCommand, memberId: Long) {
        val report = Report.file(memberId, command.targetType, command.targetId, command.reason, command.detail)
        requireTargetExists(report.targetType, report.targetId)

        if (!reportRepository.add(report)) throw AlreadyReportedException()

        if (report.targetType.autoHideable) eventPublisherPort.publish(ReportFiled(report.targetType, report.targetId))
    }

    /** 숨겨진 댓글·답변은 수에서 빠진다 — 이번 호출로 숨겨졌을 때만 부모의 수를 내린다. 답글은 어떤 수에도 잡히지 않는다. */
    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun applyAutoHide(targetType: ReportTargetType, targetId: Long) {
        val reporters = reportRepository.countDistinctReporters(targetType, targetId)
        if (!Report.reachesAutoHideThreshold(targetType, reporters)) return

        when (targetType) {
            ReportTargetType.POST -> postRepository.hideIfVisible(targetId)
            ReportTargetType.COMMENT -> commentRepository.hideIfVisible(targetId)?.let(postRepository::decreaseCommentCount)
            ReportTargetType.SPOT_QUESTION -> spotQaModerationPort.hideQuestion(targetId)
            ReportTargetType.SPOT_ANSWER -> spotQaModerationPort.hideAnswer(targetId)?.let(spotQuestionCounterPort::decreaseAnswerCount)
            ReportTargetType.SPOT_ANSWER_REPLY -> spotQaModerationPort.hideReply(targetId)
            ReportTargetType.MEMBER -> Unit
        }
    }

    private fun requireTargetExists(targetType: ReportTargetType, targetId: Long) {
        val exists = when (targetType) {
            ReportTargetType.POST -> postRepository.exists(targetId)
            ReportTargetType.COMMENT -> commentRepository.findById(targetId) != null
            ReportTargetType.MEMBER -> memberLookupPort.findAuthor(targetId) != null
            ReportTargetType.SPOT_QUESTION -> spotQaModerationPort.questionExists(targetId)
            ReportTargetType.SPOT_ANSWER -> spotQaModerationPort.answerExists(targetId)
            ReportTargetType.SPOT_ANSWER_REPLY -> spotQaModerationPort.replyExists(targetId)
        }
        if (exists) return
        throw when (targetType) {
            ReportTargetType.POST -> PostNotFoundException()
            ReportTargetType.COMMENT -> CommentNotFoundException()
            ReportTargetType.MEMBER -> MemberNotFoundException()
            ReportTargetType.SPOT_QUESTION -> QuestionNotFoundException()
            ReportTargetType.SPOT_ANSWER -> AnswerNotFoundException()
            ReportTargetType.SPOT_ANSWER_REPLY -> ReplyNotFoundException()
        }
    }
}
