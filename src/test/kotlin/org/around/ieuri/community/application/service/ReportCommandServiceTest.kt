package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.community.application.command.ReportContentCommand
import org.around.ieuri.community.application.exception.AlreadyReportedException
import org.around.ieuri.community.application.exception.CommentNotFoundException
import org.around.ieuri.community.application.exception.MemberNotFoundException
import org.around.ieuri.community.application.exception.PostNotFoundException
import org.around.ieuri.community.application.exception.ReportDetailRequiredException
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.port.out.persistence.CommentRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostCounterPort
import org.around.ieuri.community.application.port.out.persistence.PostModerationPort
import org.around.ieuri.community.application.port.out.persistence.ReportRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.SpotQaModerationPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionCounterPort
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.ReportReason
import org.around.ieuri.community.domain.vo.ReportTargetType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 신고와 자동 숨김(R16, R20, R22, R23). */
class ReportCommandServiceTest {

    private val reporterId = 5L
    private val targetPostId = 100L
    private val targetCommentId = 200L

    private val reportRepository = mockk<ReportRepositoryPort>()
    private val counterPort = mockk<PostCounterPort>(relaxed = true)
    private val moderationPort = mockk<PostModerationPort>()
    private val commentRepository = mockk<CommentRepositoryPort>()
    private val memberLookup = mockk<MemberLookupPort>()
    private val spotQaModerationPort = mockk<SpotQaModerationPort>(relaxed = true)
    private val spotQuestionCounterPort = mockk<SpotQuestionCounterPort>(relaxed = true)

    private val service = ReportCommandService(
        reportRepository, counterPort, moderationPort, commentRepository, memberLookup,
        spotQaModerationPort, spotQuestionCounterPort,
    )

    private fun comment(id: Long = targetCommentId, postId: Long = targetPostId, hidden: Boolean = false) =
        Comment.restore(
            id = id,
            postId = postId,
            authorId = 43L,
            body = "댓글",
            hidden = hidden,
            deletedAt = null,
            createdAt = Instant.EPOCH,
        )

    private fun reportPost(reason: ReportReason = ReportReason.SPAM, detail: String? = null) =
        service.report(
            ReportContentCommand(ReportTargetType.POST, targetPostId, reason, detail),
            reporterId,
        )

    @Test
    fun `신고가 기록된다`() {
        every { moderationPort.exists(targetPostId) } returns true
        every { reportRepository.save(any()) } returns true
        every { reportRepository.countDistinctReporters(any(), any()) } returns 1

        reportPost()

        verify { reportRepository.save(any()) }
    }

    @Test
    fun `같은 대상을 두 번 신고하면 거부된다`() {
        every { moderationPort.exists(targetPostId) } returns true
        every { reportRepository.save(any()) } returns false

        assertFailsWith<AlreadyReportedException> { reportPost() }
    }

    @Test
    fun `OTHER 사유에 상세 사유가 없으면 대상 조회 전에 거부된다`() {
        assertFailsWith<ReportDetailRequiredException> { reportPost(ReportReason.OTHER, null) }
        assertFailsWith<ReportDetailRequiredException> { reportPost(ReportReason.OTHER, "  ") }
        verify(exactly = 0) { moderationPort.exists(any()) }
    }

    @Test
    fun `대상 종류에 따라 다른 부재 코드가 반환된다`() {
        every { moderationPort.exists(any()) } returns false
        every { commentRepository.findById(any()) } returns null
        every { memberLookup.findAuthor(any()) } returns null

        assertFailsWith<PostNotFoundException> {
            service.report(ReportContentCommand(ReportTargetType.POST, 1L, ReportReason.SPAM, null), reporterId)
        }
        assertFailsWith<CommentNotFoundException> {
            service.report(ReportContentCommand(ReportTargetType.COMMENT, 1L, ReportReason.SPAM, null), reporterId)
        }
        assertFailsWith<MemberNotFoundException> {
            service.report(ReportContentCommand(ReportTargetType.MEMBER, 1L, ReportReason.SPAM, null), reporterId)
        }
    }

    @Test
    fun `신고자 4명이면 글은 숨겨지지 않는다`() {
        every { moderationPort.exists(targetPostId) } returns true
        every { reportRepository.save(any()) } returns true
        every { reportRepository.countDistinctReporters(ReportTargetType.POST, targetPostId) } returns 4

        reportPost()

        verify(exactly = 0) { moderationPort.hide(any()) }
    }

    @Test
    fun `서로 다른 신고자 5명이면 글이 숨겨진다`() {
        every { moderationPort.exists(targetPostId) } returns true
        every { reportRepository.save(any()) } returns true
        every { reportRepository.countDistinctReporters(ReportTargetType.POST, targetPostId) } returns 5
        every { moderationPort.hide(targetPostId) } returns true

        reportPost()

        verify(exactly = 1) { moderationPort.hide(targetPostId) }
    }

    @Test
    fun `사용자 신고는 5명에 도달해도 아무 조치가 없다`() {
        every { memberLookup.findAuthor(300L) } returns
            PostAuthorView(id = 300L, nickname = "홍길동", profileImageUrl = null)
        every { reportRepository.save(any()) } returns true

        service.report(
            ReportContentCommand(ReportTargetType.MEMBER, 300L, ReportReason.ABUSE, null),
            reporterId,
        )

        // 집계 조회조차 하지 않는다 — 자동 조치 대상이 아니기 때문이다.
        verify(exactly = 0) { reportRepository.countDistinctReporters(any(), any()) }
        verify(exactly = 0) { moderationPort.hide(any()) }
    }

    @Test
    fun `댓글이 숨겨지면 글의 댓글 수가 1 감소한다`() {
        val target = comment()
        every { commentRepository.findById(targetCommentId) } returns target
        every { commentRepository.save(any()) } returns target
        every { reportRepository.save(any()) } returns true
        every { reportRepository.countDistinctReporters(ReportTargetType.COMMENT, targetCommentId) } returns 5

        service.report(
            ReportContentCommand(ReportTargetType.COMMENT, targetCommentId, ReportReason.SPAM, null),
            reporterId,
        )

        assertTrue(target.hidden)
        verify(exactly = 1) { counterPort.decreaseCommentCount(targetPostId) }
    }

    @Test
    fun `이미 숨겨진 댓글은 댓글 수를 다시 내리지 않는다`() {
        val target = comment(hidden = true)
        every { commentRepository.findById(targetCommentId) } returns target
        every { reportRepository.save(any()) } returns true
        every { reportRepository.countDistinctReporters(ReportTargetType.COMMENT, targetCommentId) } returns 6

        service.report(
            ReportContentCommand(ReportTargetType.COMMENT, targetCommentId, ReportReason.SPAM, null),
            reporterId,
        )

        verify(exactly = 0) { counterPort.decreaseCommentCount(any()) }
    }

    @Test
    fun `상세 사유는 신고 레코드에 트리밍되어 담긴다`() {
        every { moderationPort.exists(targetPostId) } returns true
        val captured = slot<Report>()
        every { reportRepository.save(capture(captured)) } returns true
        every { reportRepository.countDistinctReporters(any(), any()) } returns 1

        reportPost(ReportReason.OTHER, "  사유  ")

        assertEquals("사유", captured.captured.detail)
    }
}
