package org.around.ieuri.community.application.report

import org.around.ieuri.community.application.comment.provided.CommentCommandUseCase
import org.around.ieuri.community.application.comment.provided.CommentQueryUseCase
import org.around.ieuri.community.application.comment.provided.exception.CommentNotFoundException
import org.around.ieuri.community.application.lookup.provided.MemberLookupUseCase
import org.around.ieuri.community.application.post.provided.PostCommandUseCase
import org.around.ieuri.community.application.post.provided.PostQueryUseCase
import org.around.ieuri.community.application.post.provided.exception.PostNotFoundException
import org.around.ieuri.community.application.question.provided.SpotQuestionCommandUseCase
import org.around.ieuri.community.application.question.provided.SpotQuestionQueryUseCase
import org.around.ieuri.community.application.question.provided.exception.AnswerNotFoundException
import org.around.ieuri.community.application.question.provided.exception.QuestionNotFoundException
import org.around.ieuri.community.application.question.provided.exception.ReplyNotFoundException
import org.around.ieuri.community.application.report.provided.ReportAutoHideUseCase
import org.around.ieuri.community.application.report.provided.ReportCommandUseCase
import org.around.ieuri.community.application.report.provided.dto.ReportContentCommand
import org.around.ieuri.community.application.report.required.ReportEventPublisherPort
import org.around.ieuri.community.application.report.required.ReportRepository
import org.around.ieuri.community.application.shared.exception.MemberNotFoundException
import org.around.ieuri.community.domain.report.Report
import org.around.ieuri.community.domain.report.ReportTargetType
import org.around.ieuri.community.domain.report.event.ReportFiled
import org.around.ieuri.community.domain.shared.AlreadyReportedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 신고. 기록만 하고, 자동 숨김은 커밋 뒤 [applyAutoHide]가 신고자 수를 다시 세어 정한다.
 * - 회원 신고는 기록만 한다(POL-041). 회원 존재 확인(auth)은 첫 쿼리 전에 한다.
 */
@Service
@Transactional
class ReportModifyService(
    private val reportRepository: ReportRepository,
    private val postCommandUseCase: PostCommandUseCase,
    private val postQueryUseCase: PostQueryUseCase,
    private val commentCommandUseCase: CommentCommandUseCase,
    private val commentQueryUseCase: CommentQueryUseCase,
    private val questionCommandUseCase: SpotQuestionCommandUseCase,
    private val questionQueryUseCase: SpotQuestionQueryUseCase,
    private val memberLookup: MemberLookupUseCase,
    private val eventPublisherPort: ReportEventPublisherPort,
) : ReportCommandUseCase,
    ReportAutoHideUseCase {

    override fun report(command: ReportContentCommand, memberId: Long) {
        val report = Report.file(memberId, command.targetType, command.targetId, command.reason, command.detail)
        requireTargetExists(report.targetType, report.targetId)

        if (!reportRepository.add(report)) throw AlreadyReportedException()

        if (report.targetType.autoHideable) eventPublisherPort.publish(ReportFiled(report.targetType, report.targetId))
    }

    /**
     * 숨기는 것은 대상 애그리게이트가 한다 — 숨겨진 댓글·답변이 수에서 빠지는 것도 거기서 정한다.
     * - 커밋 뒤 리스너가 부르므로 새 트랜잭션이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun applyAutoHide(targetType: ReportTargetType, targetId: Long) {
        val reporters = reportRepository.countDistinctReporters(targetType, targetId)
        if (!Report.reachesAutoHideThreshold(targetType, reporters)) return

        when (targetType) {
            ReportTargetType.POST -> postCommandUseCase.hideReported(targetId)
            ReportTargetType.COMMENT -> commentCommandUseCase.hideReported(targetId)
            ReportTargetType.SPOT_QUESTION -> questionCommandUseCase.hideReportedQuestion(targetId)
            ReportTargetType.SPOT_ANSWER -> questionCommandUseCase.hideReportedAnswer(targetId)
            ReportTargetType.SPOT_ANSWER_REPLY -> questionCommandUseCase.hideReportedReply(targetId)
            ReportTargetType.MEMBER -> Unit
        }
    }

    private fun requireTargetExists(targetType: ReportTargetType, targetId: Long) {
        val exists = when (targetType) {
            ReportTargetType.POST -> postQueryUseCase.exists(targetId)
            ReportTargetType.COMMENT -> commentQueryUseCase.exists(targetId)
            ReportTargetType.MEMBER -> memberLookup.member(targetId) != null
            ReportTargetType.SPOT_QUESTION -> questionQueryUseCase.questionExists(targetId)
            ReportTargetType.SPOT_ANSWER -> questionQueryUseCase.answerExists(targetId)
            ReportTargetType.SPOT_ANSWER_REPLY -> questionQueryUseCase.replyExists(targetId)
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
