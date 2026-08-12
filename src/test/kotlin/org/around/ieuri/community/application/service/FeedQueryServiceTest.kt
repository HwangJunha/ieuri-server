package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.common.security.ForbiddenException
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.`in`.query.ConversationSubscription
import org.around.ieuri.community.application.port.out.event.ConversationEventPort
import org.around.ieuri.community.application.port.out.query.ConversationMessagesSearch
import org.around.ieuri.community.application.port.out.query.ConversationQueryPort
import org.around.ieuri.community.application.port.out.query.ConversationsSearch
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.FeedQueryPort
import org.around.ieuri.community.application.port.out.query.FeedSearch
import org.around.ieuri.community.application.query.ConversationView
import org.around.ieuri.community.application.query.MessageView
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 피드·대화 조회(SPEC-0017 R4·R8·R11). */
class FeedQueryServiceTest {

    private val memberId = 7L
    private val conversationId = 300L

    private val feedQueryPort = mockk<FeedQueryPort>()
    private val communityQueryPort = mockk<CommunityQueryPort>()
    private val conversationQueryPort = mockk<ConversationQueryPort>()
    private val conversationEventPort = mockk<ConversationEventPort>(relaxed = true)

    private val policy = CommunityPolicy(
        maxPageSize = 50,
        commentPreviewSize = 3,
        maxRadiusMeters = 100_000,
        uploadUrlTtl = Duration.ofMinutes(30),
        maxUploadBytes = 10L * 1024 * 1024,
        allowedContentTypes = setOf("image/jpeg"),
    )

    private val feedService = FeedQueryService(feedQueryPort, communityQueryPort, policy)
    private val conversationService = ConversationQueryService(
        conversationQueryPort, conversationEventPort, policy,
    )

    private fun givenBlocked(blocked: Set<Long>) {
        every { communityQueryPort.findBlockedMemberIds(memberId) } returns blocked
    }

    private fun givenEmptyFeed(search: CapturingSlot<FeedSearch>) {
        every { feedQueryPort.findFeed(capture(search)) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)
    }

    /**
     * AC11-1·11-2 — **차단 필터가 질의로 들어간다.** 조회 후 걸러내면 페이지 크기가
     * 들쭉날쭉해지고 hasNextPage가 거짓말을 한다.
     */
    @Test
    fun `차단 관계가 조회 조건으로 전달된다`() {
        givenBlocked(setOf(9L, 11L))
        val search = slot<FeedSearch>()
        givenEmptyFeed(search)

        feedService.feed(first = 20, after = null, memberId = memberId)

        assertEquals(setOf(9L, 11L), search.captured.viewer.blockedMemberIds)
        assertEquals(memberId, search.captured.viewer.memberId)
    }

    /** AC4-3 — 상한 초과는 **오류가 아니라 절삭이다**(POL-018). */
    @Test
    fun `상한을 넘는 first는 오류가 아니라 절삭된다`() {
        givenBlocked(emptySet())
        val search = slot<FeedSearch>()
        givenEmptyFeed(search)

        feedService.feed(first = 500, after = null, memberId = memberId)

        assertEquals(50, search.captured.first)
    }

    @Test
    fun `first를 주지 않으면 계약의 기본값을 쓴다`() {
        givenBlocked(emptySet())
        val search = slot<FeedSearch>()
        givenEmptyFeed(search)

        feedService.feed(first = null, after = null, memberId = memberId)

        assertEquals(20, search.captured.first)
    }

    /**
     * **지연 필드가 실제로 지연되는지**를 서비스 수준에서 고정한다(CLAUDE.md).
     *
     * 사진·좋아요 여부는 클라이언트가 그 필드를 고를 때만 읽어야 한다 — 목록 조회가 미리
     * 채우면 `{ id body }`만 물어도 조회가 세 번 붙고, 지연 필드로 나눈 의미가 사라진다.
     * 컴파일로는 증명되지 않는 성질이라 여기서 못 박는다.
     */
    @Test
    fun `피드 목록 조회는 사진과 좋아요를 읽지 않는다`() {
        givenBlocked(emptySet())
        every { feedQueryPort.findFeed(any()) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        feedService.feed(first = 20, after = null, memberId = memberId)

        io.mockk.verify(exactly = 0) { feedQueryPort.findPhotoKeysOf(any()) }
        io.mockk.verify(exactly = 0) { feedQueryPort.findLikedFeedItemIds(any(), any()) }
    }

    /** 단건 조회도 마찬가지다 — 딥링크 진입에서도 고르지 않은 필드는 읽지 않는다. */
    @Test
    fun `피드 단건 조회도 사진과 좋아요를 읽지 않는다`() {
        givenBlocked(emptySet())
        every { feedQueryPort.findFeedItem(any(), any()) } returns null

        feedService.feedItem(feedItemId = 100L, memberId = memberId)

        io.mockk.verify(exactly = 0) { feedQueryPort.findPhotoKeysOf(any()) }
        io.mockk.verify(exactly = 0) { feedQueryPort.findLikedFeedItemIds(any(), any()) }
    }

    /** AC4-4 — 더 이상 항목이 없으면 빈 목록이며 오류가 아니다. */
    @Test
    fun `소진된 페이지는 빈 목록과 hasNextPage false다`() {
        givenBlocked(emptySet())
        every { feedQueryPort.findFeed(any()) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = true)

        val page = feedService.feed(first = 20, after = "cursor", memberId = memberId)

        assertEquals(0, page.edges.size)
        assertEquals(false, page.pageInfo.hasNextPage)
    }

    // ── 대화 조회(R8) ────────────────────────────────────────────────

    /**
     * 참여자 판정은 **읽기 포트가 한다** — 조회가 참여자 조건을 질의에 담으므로, 결과가
     * 있다는 것이 곧 "참여자다"이고 null이 "없거나 볼 수 없다"이다.
     */
    private fun conversationView() = ConversationView(
        id = conversationId,
        counterpartId = 9L,
        lastMessage = null,
        unreadCount = 0,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `대화 목록의 상한도 절삭된다`() {
        val search = slot<ConversationsSearch>()
        every { conversationQueryPort.findConversations(capture(search)) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        conversationService.conversations(first = 500, after = null, memberId = memberId)

        assertEquals(50, search.captured.first)
    }

    @Test
    fun `메시지 조회의 기본값은 30이다`() {
        every { conversationQueryPort.findConversation(conversationId, memberId) } returns conversationView()
        val search = slot<ConversationMessagesSearch>()
        every { conversationQueryPort.findMessages(capture(search)) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        conversationService.conversationMessages(conversationId, null, null, memberId)

        assertEquals(30, search.captured.first)
    }

    /**
     * AC8-3 — **조회의 권한 실패는 최상위 errors[]의 FORBIDDEN이다.** 뮤테이션의
     * NOT_CONVERSATION_PARTICIPANT와 갈리는 지점이며, 조회는 볼 자격 자체가 없어 부분
     * 성공이 성립하지 않는다.
     */
    @Test
    fun `참여자가 아닌 대화의 메시지 조회는 FORBIDDEN이다`() {
        every { conversationQueryPort.findConversation(conversationId, memberId) } returns null

        assertFailsWith<ForbiddenException> {
            conversationService.conversationMessages(conversationId, null, null, memberId)
        }
    }

    /** 조회 경로에서는 부재도 FORBIDDEN이다 — 구분하면 대화 id 존재 확인 수단이 된다. */
    @Test
    fun `없는 대화의 메시지 조회도 FORBIDDEN이다`() {
        every { conversationQueryPort.findConversation(conversationId, memberId) } returns null

        assertFailsWith<ForbiddenException> {
            conversationService.conversationMessages(conversationId, null, null, memberId)
        }
    }

    /** AC10-3 — **구독 시작 시점에** 참여자를 검사한다(POL-048). */
    @Test
    fun `참여자가 아니면 구독이 FORBIDDEN으로 거부된다`() {
        every { conversationQueryPort.findConversation(conversationId, memberId) } returns null

        assertFailsWith<ForbiddenException> {
            conversationService.typingIndicator(conversationId, memberId)
        }
    }

    /**
     * 새 메시지 구독도 **구독 시작 시점에** 참여자를 검사한다 — 타이핑과 같은 규약이다.
     */
    @Test
    fun `참여자가 아니면 새 메시지 구독이 FORBIDDEN으로 거부된다`() {
        every { conversationQueryPort.findConversation(conversationId, memberId) } returns null

        assertFailsWith<ForbiddenException> {
            conversationService.newMessage(conversationId, memberId)
        }
    }

    /**
     * **이벤트의 id로 DB를 다시 읽어 내려준다** — 본문을 브로커에 싣지 않으므로(POL-013)
     * 값의 원본이 항상 DB다. 이 매핑이 빠지면 구독자가 id만 받게 된다.
     */
    @Test
    fun `새 메시지 구독은 이벤트의 id로 DB에서 메시지를 읽어 내려준다`() {
        every { conversationQueryPort.findConversation(conversationId, memberId) } returns conversationView()
        val source = RecordingSubscription<Long>()
        every { conversationEventPort.subscribeNewMessage(conversationId) } returns source
        val stored = MessageView(
            id = 500L,
            body = "안녕",
            senderId = 9L,
            ownedByViewer = false,
            createdAt = Instant.EPOCH,
        )
        every { conversationQueryPort.findMessage(500L, memberId) } returns stored

        val received = mutableListOf<MessageView>()
        conversationService.newMessage(conversationId, memberId).onEvent { received += it }
        source.emit(500L)

        assertEquals(listOf(stored), received)
    }

    /** 읽는 사이에 사라진 메시지는 **조용히 건너뛴다** — 빈 말풍선을 내려보내지 않는다. */
    @Test
    fun `사라진 메시지의 이벤트는 흘려보낸다`() {
        every { conversationQueryPort.findConversation(conversationId, memberId) } returns conversationView()
        val source = RecordingSubscription<Long>()
        every { conversationEventPort.subscribeNewMessage(conversationId) } returns source
        every { conversationQueryPort.findMessage(500L, memberId) } returns null

        val received = mutableListOf<MessageView>()
        conversationService.newMessage(conversationId, memberId).onEvent { received += it }
        source.emit(500L)

        assertEquals(emptyList(), received)
    }

    @Test
    fun `참여자면 구독이 열린다`() {
        every { conversationQueryPort.findConversation(conversationId, memberId) } returns conversationView()

        conversationService.typingIndicator(conversationId, memberId)

        io.mockk.verify { conversationEventPort.subscribeTyping(conversationId) }
    }
}

private typealias CapturingSlot<T> = io.mockk.CapturingSlot<T>

/** 이벤트를 손으로 흘려보내는 스텁 구독. 브로커 없이 매핑 동작만 확인한다. */
private class RecordingSubscription<T> : ConversationSubscription<T> {
    private var listener: ((T) -> Unit)? = null

    override fun onEvent(listener: (T) -> Unit) {
        this.listener = listener
    }

    override fun cancel() {
        listener = null
    }

    fun emit(event: T) {
        listener?.invoke(event)
    }
}
