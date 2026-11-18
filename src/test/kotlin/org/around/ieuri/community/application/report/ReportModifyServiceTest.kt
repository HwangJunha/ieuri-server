package org.around.ieuri.community.application.report

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.comment.required.CommentRepository
import org.around.ieuri.community.application.lookup.provided.MemberLookupUseCase
import org.around.ieuri.community.application.post.provided.exception.PostNotFoundException
import org.around.ieuri.community.application.post.required.PostRepository
import org.around.ieuri.community.application.question.required.SpotQuestionRepository
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
    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val commentRepository = mockk<CommentRepository>()
    private val questionRepository = mockk<SpotQuestionRepository>(relaxed = true)
    private val memberLookup = mockk<MemberLookupUseCase>()
    private val eventPublisher = mockk<ReportEventPublisherPort>(relaxUnitFun = true)
    private val service = ReportModifyService(reportRepository, postRepository, commentRepository, questionRepository, memberLookup, eventPublisher)

    @Test
    fun `자동 숨김 대상 신고는 기록 뒤 이벤트를 낸다`() {
        every { postRepository.exists(1) } returns true
        every { reportRepository.add(any()) } returns true

        service.report(ReportContentCommand(ReportTargetType.POST, 1, ReportReason.SPAM, null), memberId = 7)

        verify { eventPublisher.publish(ReportFiled(ReportTargetType.POST, 1)) }
    }

    @Test
    fun `없는 대상과 중복 신고는 거부하고 이벤트를 내지 않는다`() {
        every { postRepository.exists(1) } returns false
        assertFailsWith<PostNotFoundException> { service.report(ReportContentCommand(ReportTargetType.POST, 1, ReportReason.SPAM, null), 7) }

        every { postRepository.exists(2) } returns true
        every { reportRepository.add(any()) } returns false
        assertFailsWith<AlreadyReportedException> { service.report(ReportContentCommand(ReportTargetType.POST, 2, ReportReason.SPAM, null), 7) }

        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `임계값 미만이면 숨기지 않는다`() {
        every { reportRepository.countDistinctReporters(ReportTargetType.POST, 1) } returns Report.AUTO_HIDE_THRESHOLD - 1

        service.applyAutoHide(ReportTargetType.POST, 1)

        verify(exactly = 0) { postRepository.hideIfVisible(any()) }
    }

    @Test
    fun `댓글·답변은 이번에 숨겨졌을 때만 부모의 수를 내린다`() {
        every { reportRepository.countDistinctReporters(any(), any()) } returns Report.AUTO_HIDE_THRESHOLD
        every { commentRepository.hideIfVisible(10) } returns 1
        every { commentRepository.hideIfVisible(11) } returns null
        every { questionRepository.hideAnswerIfVisible(20) } returns 2

        service.applyAutoHide(ReportTargetType.COMMENT, 10)
        service.applyAutoHide(ReportTargetType.COMMENT, 11)
        service.applyAutoHide(ReportTargetType.SPOT_ANSWER, 20)

        verify(exactly = 1) { postRepository.decreaseCommentCount(1) }
        verify(exactly = 1) { questionRepository.decreaseAnswerCount(2) }
    }

    @Test
    fun `질문·답글은 숨기기만 한다`() {
        every { reportRepository.countDistinctReporters(any(), any()) } returns Report.AUTO_HIDE_THRESHOLD

        service.applyAutoHide(ReportTargetType.SPOT_QUESTION, 5)
        service.applyAutoHide(ReportTargetType.SPOT_ANSWER_REPLY, 6)

        verify { questionRepository.hideIfVisible(5) }
        verify { questionRepository.hideReplyIfVisible(6) }
        verify(exactly = 0) { questionRepository.decreaseAnswerCount(any()) }
    }
}
