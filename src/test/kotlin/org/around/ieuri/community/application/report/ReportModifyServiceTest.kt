package org.around.ieuri.community.application.report

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.comment.provided.CommentCommandUseCase
import org.around.ieuri.community.application.comment.provided.CommentQueryUseCase
import org.around.ieuri.community.application.comment.provided.exception.CommentNotFoundException
import org.around.ieuri.community.application.lookup.provided.MemberLookupUseCase
import org.around.ieuri.community.application.post.provided.PostCommandUseCase
import org.around.ieuri.community.application.post.provided.PostQueryUseCase
import org.around.ieuri.community.application.post.provided.exception.PostNotFoundException
import org.around.ieuri.community.application.question.provided.SpotQuestionCommandUseCase
import org.around.ieuri.community.application.question.provided.SpotQuestionQueryUseCase
import org.around.ieuri.community.application.report.provided.dto.ReportContentCommand
import org.around.ieuri.community.application.report.required.ReportEventPublisherPort
import org.around.ieuri.community.application.report.required.ReportRepository
import org.around.ieuri.community.domain.report.Report
import org.around.ieuri.community.domain.report.ReportTargetType
import org.around.ieuri.community.domain.report.event.ReportFiled
import org.around.ieuri.community.domain.shared.AlreadyReportedException
import org.around.ieuri.community.domain.shared.ReportReason
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReportModifyServiceTest {

    private val reportRepository = mockk<ReportRepository>()
    private val postCommand = mockk<PostCommandUseCase>(relaxed = true)
    private val postQuery = mockk<PostQueryUseCase>()
    private val commentCommand = mockk<CommentCommandUseCase>(relaxed = true)
    private val commentQuery = mockk<CommentQueryUseCase>()
    private val questionCommand = mockk<SpotQuestionCommandUseCase>(relaxed = true)
    private val questionQuery = mockk<SpotQuestionQueryUseCase>()
    private val memberLookup = mockk<MemberLookupUseCase>()
    private val eventPublisher = mockk<ReportEventPublisherPort>(relaxUnitFun = true)
    private val service = ReportModifyService(reportRepository, postCommand, postQuery, commentCommand, commentQuery, questionCommand, questionQuery, memberLookup, eventPublisher)

    @Test
    fun `자동 숨김 대상 신고는 기록 뒤 이벤트를 낸다`() {
        every { postQuery.exists(1) } returns true
        every { reportRepository.add(any()) } returns true

        service.report(ReportContentCommand(ReportTargetType.POST, 1, ReportReason.SPAM, null), memberId = 7)

        verify { eventPublisher.publish(ReportFiled(ReportTargetType.POST, 1)) }
    }

    @Test
    fun `없는 대상과 중복 신고는 거부하고 이벤트를 내지 않는다`() {
        every { postQuery.exists(1) } returns false
        assertFailsWith<PostNotFoundException> { service.report(ReportContentCommand(ReportTargetType.POST, 1, ReportReason.SPAM, null), 7) }

        every { postQuery.exists(2) } returns true
        every { reportRepository.add(any()) } returns false
        assertFailsWith<AlreadyReportedException> { service.report(ReportContentCommand(ReportTargetType.POST, 2, ReportReason.SPAM, null), 7) }

        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    /** 대상 종류마다 그 애그리게이트의 조회 유스케이스로 존재를 본다. */
    @Test
    fun `댓글·질문·답변·답글의 존재는 각 애그리게이트에 묻는다`() {
        every { commentQuery.exists(3) } returns false
        every { questionQuery.questionExists(4) } returns true
        every { questionQuery.answerExists(5) } returns true
        every { questionQuery.replyExists(6) } returns true
        every { reportRepository.add(any()) } returns true

        assertFailsWith<CommentNotFoundException> { service.report(ReportContentCommand(ReportTargetType.COMMENT, 3, ReportReason.SPAM, null), 7) }
        service.report(ReportContentCommand(ReportTargetType.SPOT_QUESTION, 4, ReportReason.SPAM, null), 7)
        service.report(ReportContentCommand(ReportTargetType.SPOT_ANSWER, 5, ReportReason.SPAM, null), 7)
        service.report(ReportContentCommand(ReportTargetType.SPOT_ANSWER_REPLY, 6, ReportReason.SPAM, null), 7)

        verify(exactly = 3) { reportRepository.add(any()) }
    }

    @Test
    fun `임계값 미만이면 숨기지 않는다`() {
        every { reportRepository.countDistinctReporters(ReportTargetType.POST, 1) } returns Report.AUTO_HIDE_THRESHOLD - 1

        service.applyAutoHide(ReportTargetType.POST, 1)

        verify(exactly = 0) { postCommand.hideReported(any()) }
    }

    /** 숨기는 것과 부모의 수를 내리는 것은 대상 애그리게이트가 한다. */
    @Test
    fun `임계값에 이르면 대상 애그리게이트에 숨김을 맡긴다`() {
        every { reportRepository.countDistinctReporters(any(), any()) } returns Report.AUTO_HIDE_THRESHOLD

        service.applyAutoHide(ReportTargetType.POST, 1)
        service.applyAutoHide(ReportTargetType.COMMENT, 10)
        service.applyAutoHide(ReportTargetType.SPOT_QUESTION, 5)
        service.applyAutoHide(ReportTargetType.SPOT_ANSWER, 20)
        service.applyAutoHide(ReportTargetType.SPOT_ANSWER_REPLY, 6)

        verify(exactly = 1) { postCommand.hideReported(1) }
        verify(exactly = 1) { commentCommand.hideReported(10) }
        verify(exactly = 1) { questionCommand.hideReportedQuestion(5) }
        verify(exactly = 1) { questionCommand.hideReportedAnswer(20) }
        verify(exactly = 1) { questionCommand.hideReportedReply(6) }
    }
}
