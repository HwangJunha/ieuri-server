package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.ReportContentCommand
import org.around.ieuri.community.application.exception.AlreadyReportedException
import org.around.ieuri.community.application.exception.AnswerNotFoundException
import org.around.ieuri.community.application.exception.CommentNotFoundException
import org.around.ieuri.community.application.exception.MemberNotFoundException
import org.around.ieuri.community.application.exception.PostNotFoundException
import org.around.ieuri.community.application.exception.QuestionNotFoundException
import org.around.ieuri.community.application.exception.ReplyNotFoundException
import org.around.ieuri.community.application.exception.ReportDetailRequiredException
import org.around.ieuri.community.application.port.`in`.command.ReportCommandUseCase
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.port.out.persistence.CommentRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostCounterPort
import org.around.ieuri.community.application.port.out.persistence.PostModerationPort
import org.around.ieuri.community.application.port.out.persistence.ReportRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.SpotQaModerationPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionCounterPort
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.ReportTargetType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 신고. 기록한 뒤 자동 숨김 임계값 도달 여부를 같은 트랜잭션에서 확인한다.
 * - 공개 콘텐츠만 서로 다른 신고자 수가 임계값에 닿으면 숨긴다. MEMBER 신고는 기록만 한다.
 * - 임계값은 대상 규모에 따라 낮추지 않는다(SPEC-0026).
 * - 댓글·답변이 숨겨지면 부모의 commentCount·answerCount를 내린다.
 * - DM 메시지 신고는 MessageReportCommandService가 다룬다(POL-049).
 */
@Service
class ReportCommandService(
    private val reportRepositoryPort: ReportRepositoryPort,
    private val postCounterPort: PostCounterPort,
    private val postModerationPort: PostModerationPort,
    private val commentRepositoryPort: CommentRepositoryPort,
    private val memberLookupPort: MemberLookupPort,
    private val spotQaModerationPort: SpotQaModerationPort,
    private val spotQuestionCounterPort: SpotQuestionCounterPort,
) : ReportCommandUseCase {

    @Transactional
    override fun report(command: ReportContentCommand, memberId: Long) {
        if (command.reason.requiresDetail && command.detail?.trim().isNullOrEmpty()) {
            throw ReportDetailRequiredException()
        }
        requireTargetExists(command.targetType, command.targetId)

        val inserted = reportRepositoryPort.save(
            Report.create(
                reporterId = memberId,
                targetType = command.targetType,
                targetId = command.targetId,
                reason = command.reason,
                detail = command.detail,
            ),
        )
        // 같은 대상을 두 번 신고할 수 없다.
        if (!inserted) throw AlreadyReportedException()

        applyAutoHideIfNeeded(command.targetType, command.targetId)
    }

    private fun requireTargetExists(targetType: ReportTargetType, targetId: Long) {
        val exists = when (targetType) {
            ReportTargetType.POST -> postModerationPort.exists(targetId)
            ReportTargetType.COMMENT -> commentRepositoryPort.findById(targetId) != null
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

    /** MEMBER는 [ReportTargetType.autoHideable]이 false라 집계 조회조차 하지 않는다. */
    private fun applyAutoHideIfNeeded(targetType: ReportTargetType, targetId: Long) {
        if (!targetType.autoHideable) return

        val reporters = reportRepositoryPort.countDistinctReporters(targetType, targetId)
        if (!Report.reachesAutoHideThreshold(targetType, reporters)) return

        when (targetType) {
            // 이미 숨겨져 있으면 0건 갱신이다.
            ReportTargetType.POST -> postModerationPort.hide(targetId)

            ReportTargetType.COMMENT -> commentRepositoryPort.findById(targetId)?.let {
                // 이미 숨겨져 있으면 commentCount를 다시 내리지 않는다.
                if (it.hidden) return
                it.hide()
                commentRepositoryPort.save(it)
                postCounterPort.decreaseCommentCount(it.postId)
            }

            ReportTargetType.SPOT_QUESTION -> spotQaModerationPort.hideQuestion(targetId)

            // 질문의 answerCount를 함께 내린다. 이미 숨겨져 있으면 null이라 두 번 내리지 않는다.
            ReportTargetType.SPOT_ANSWER -> spotQaModerationPort.hideAnswer(targetId)
                ?.let { questionId -> spotQuestionCounterPort.decreaseAnswerCount(questionId) }

            // 답글은 어떤 계수에도 잡히지 않아 숨기기만 한다.
            ReportTargetType.SPOT_ANSWER_REPLY -> spotQaModerationPort.hideReply(targetId)

            ReportTargetType.MEMBER -> Unit
        }
    }
}
