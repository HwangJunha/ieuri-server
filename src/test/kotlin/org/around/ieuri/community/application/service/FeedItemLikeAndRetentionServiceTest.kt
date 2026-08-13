package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.command.LikeFeedItemCommand
import org.around.ieuri.community.application.exception.FeedItemNotFoundException
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.persistence.FeedItemCounterPort
import org.around.ieuri.community.application.port.out.persistence.FeedItemLikeRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.FeedItemModerationPort
import org.around.ieuri.community.application.port.out.persistence.MessagePurgePort
import org.around.ieuri.community.application.port.out.query.FeedQueryPort
import org.around.ieuri.community.domain.aggregate.Message
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 피드 좋아요 멱등성(R6 — AC6-1~6-3)과 보관 기간 삭제(R16). */
class FeedItemLikeAndRetentionServiceTest {

    private val memberId = 7L
    private val feedItemId = 100L
    private val now = Instant.parse("2026-08-12T00:00:00Z")

    private val likeRepository = mockk<FeedItemLikeRepositoryPort>()
    private val counterPort = mockk<FeedItemCounterPort>(relaxed = true)
    private val moderationPort = mockk<FeedItemModerationPort>()
    private val feedQueryPort = mockk<FeedQueryPort>()

    private val likeService = FeedItemLikeCommandService(
        likeRepository, counterPort, moderationPort, feedQueryPort,
    )

    private fun givenExistingFeedItem() {
        every { moderationPort.exists(feedItemId) } returns true
    }

    /** AC6-1 — 좋아요하면 수가 1 늘어난다. */
    @Test
    fun `좋아요하면 수가 오른다`() {
        givenExistingFeedItem()
        every { likeRepository.save(feedItemId, memberId) } returns true
        every { feedQueryPort.findLikeCount(feedItemId) } returns 1

        val count = likeService.like(LikeFeedItemCommand(feedItemId), memberId)

        assertEquals(1, count)
        verify(exactly = 1) { counterPort.increaseLikeCount(feedItemId) }
    }

    /**
     * AC6-2 — **연산은 멱등이다.** 저장소가 이미 있다고 답하면 집계를 올리지 않는다 —
     * 올리면 카운트가 실제 좋아요 행 수보다 커진다.
     */
    @Test
    fun `이미 좋아요한 피드에 다시 눌러도 수가 오르지 않는다`() {
        givenExistingFeedItem()
        every { likeRepository.save(feedItemId, memberId) } returns false
        every { feedQueryPort.findLikeCount(feedItemId) } returns 1

        val count = likeService.like(LikeFeedItemCommand(feedItemId), memberId)

        assertEquals(1, count)
        verify(exactly = 0) { counterPort.increaseLikeCount(any()) }
    }

    /** AC6-3 — 좋아요하지 않은 피드를 취소해도 오류가 아니고 음수가 되지 않는다. */
    @Test
    fun `좋아요하지 않은 피드의 취소는 오류가 아니다`() {
        givenExistingFeedItem()
        every { likeRepository.delete(feedItemId, memberId) } returns false
        every { feedQueryPort.findLikeCount(feedItemId) } returns 0

        val count = likeService.unlike(LikeFeedItemCommand(feedItemId), memberId)

        assertEquals(0, count)
        verify(exactly = 0) { counterPort.decreaseLikeCount(any()) }
    }

    @Test
    fun `좋아요를 취소하면 수가 내린다`() {
        givenExistingFeedItem()
        every { likeRepository.delete(feedItemId, memberId) } returns true
        every { feedQueryPort.findLikeCount(feedItemId) } returns 0

        likeService.unlike(LikeFeedItemCommand(feedItemId), memberId)

        verify(exactly = 1) { counterPort.decreaseLikeCount(feedItemId) }
    }

    @Test
    fun `없는 피드에 좋아요하면 FEED_ITEM_NOT_FOUND다`() {
        every { moderationPort.exists(feedItemId) } returns false

        assertFailsWith<FeedItemNotFoundException> {
            likeService.like(LikeFeedItemCommand(feedItemId), memberId)
        }
    }

    // ── 보관 기간 삭제(R16) ──────────────────────────────────────────

    /**
     * AC16-1 — **기준 시각은 지금으로부터 보관 기간 이전이다.** 대화의 활동과 무관하게
     * 메시지 자신의 생성 시각으로만 판정한다(AC16-3).
     */
    @Test
    fun `삭제 배치는 보관 기간 이전 메시지를 회차 상한만큼 지운다`() {
        val purgePort = mockk<MessagePurgePort>()
        val clock = mockk<ClockPort> { every { now() } returns now }
        val service = MessageRetentionService(purgePort, clock)
        every { purgePort.deleteCreatedBefore(any(), any()) } returns 42

        val deleted = service.purgeExpiredBatch(limit = 100)

        assertEquals(42, deleted)
        verify { purgePort.deleteCreatedBefore(Message.retentionCutoff(now), 100) }
    }

    @Test
    fun `회차 상한은 양수여야 한다`() {
        val service = MessageRetentionService(
            mockk<MessagePurgePort>(),
            mockk<ClockPort> { every { now() } returns now },
        )

        assertFailsWith<IllegalArgumentException> { service.purgeExpiredBatch(limit = 0) }
    }
}
