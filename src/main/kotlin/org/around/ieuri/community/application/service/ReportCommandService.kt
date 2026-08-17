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
 * 신고. 기록한 뒤 자동 숨김 임계값 도달 여부를 같은 트랜잭션에서 확인한다 — 알림이 비범위라
 * 도메인 이벤트를 발행할 이유가 없고, 동기 처리로 충분하다.
 *
 * **대상 종류에 따라 갈린다.** 공개 콘텐츠(POST·COMMENT·장소 질문/답변/답글)만 서로 다른
 * 신고자 5명에서 숨김으로 전환되고, MEMBER 신고는 기록만 하고 어떤 자동 조치도 하지 않는다 —
 * 신고 몇 건으로 한 사람의 글이 일괄로 사라지면 조직적 신고가 곧 침묵 수단이 된다.
 *
 * **임계값은 대상 규모에 따라 낮추지 않는다**(SPEC-0026 R11). 질문/답변은 그 장소를
 * 좋아요한 회원만 참여해 모집단이 작지만, 임계값이 작아질수록 신고는 상대를 침묵시키는
 * 도구에 가까워진다 — 소규모 장소에서 피해를 멈추는 수단은 자동 숨김이 아니라 차단이다.
 *
 * **DM 메시지는 이 서비스가 다루지 않는다.** 다건 신고라 별도 경로(MessageReportCommandService)이며
 * 누적 자동 숨김도 적용되지 않는다(POL-049).
 *
 * 댓글이 숨김으로 전환되면 글의 commentCount를 함께 내린다 — 보이지 않는 댓글이 숫자에는
 * 잡히면 유저는 세어 보고 버그라고 판단한다.
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
        // 같은 대상을 두 번 신고할 수 없다. 거부하지 않고 통과시키면 한 사람이 임계값을
        // 혼자 채울 수 있다.
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
            // 이미 숨겨져 있으면 0건이 갱신되고 false다 — 중복 처리가 조건절에서 걸러진다.
            ReportTargetType.POST -> postModerationPort.hide(targetId)

            ReportTargetType.COMMENT -> commentRepositoryPort.findById(targetId)?.let {
                // 이미 숨겨져 있으면 commentCount를 다시 내리지 않는다 — 두 번 내리면
                // 숫자가 실제보다 작아진다.
                if (it.hidden) return
                it.hide()
                commentRepositoryPort.save(it)
                postCounterPort.decreaseCommentCount(it.postId)
            }

            ReportTargetType.SPOT_QUESTION -> spotQaModerationPort.hideQuestion(targetId)

            // 답변이 숨겨지면 질문의 answerCount를 함께 내린다 — 댓글과 같은 근거로,
            // 보이지 않는 답변이 숫자에는 잡히면 유저는 세어 보고 버그라고 판단한다.
            // 이미 숨겨져 있으면 null이라 두 번 내려가지 않는다.
            ReportTargetType.SPOT_ANSWER -> spotQaModerationPort.hideAnswer(targetId)
                ?.let { questionId -> spotQuestionCounterPort.decreaseAnswerCount(questionId) }

            // 답글은 어떤 계수에도 잡히지 않아 숨기기만 한다.
            ReportTargetType.SPOT_ANSWER_REPLY -> spotQaModerationPort.hideReply(targetId)

            // DM 메시지가 여기 없는 것은 열거값 자체가 없기 때문이다 — 다건 신고라
            // 별도 경로다(POL-049).
            ReportTargetType.MEMBER -> Unit
        }
    }
}
