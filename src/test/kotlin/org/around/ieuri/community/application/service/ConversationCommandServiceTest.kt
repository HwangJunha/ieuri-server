package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.community.application.command.MarkConversationReadCommand
import org.around.ieuri.community.application.command.NotifyTypingCommand
import org.around.ieuri.community.application.command.SendMessageCommand
import org.around.ieuri.community.application.command.StartConversationCommand
import org.around.ieuri.community.application.event.NewMessageStored
import org.around.ieuri.community.application.exception.BlockedException
import org.around.ieuri.community.application.exception.ConversationNotFoundException
import org.around.ieuri.community.application.exception.MemberNotFoundException
import org.around.ieuri.community.application.exception.NotConversationParticipantException
import org.around.ieuri.community.application.exception.SelfConversationException
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.around.ieuri.community.application.port.out.event.ConversationEventPort
import org.around.ieuri.community.application.port.out.persistence.ConversationRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.MessageRepositoryPort
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.ConversationQueryPort
import org.around.ieuri.community.application.port.out.query.SpotQaQueryPort
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 대화 시작·메시지 전송·타이핑(SPEC-0017 R9·R10·R11, SPEC-0021 R2). */
class ConversationCommandServiceTest {

    private val memberId = 7L
    private val authorId = 9L
    private val conversationId = 300L
    private val now = Instant.parse("2026-08-12T00:00:00Z")
    private val language = ContentLanguage.EN

    private val conversationRepository = mockk<ConversationRepositoryPort>(relaxed = true)
    private val messageRepository = mockk<MessageRepositoryPort>()
    private val memberLookupPort = mockk<MemberLookupPort>()
    private val communityQueryPort = mockk<CommunityQueryPort>()
    private val conversationEventPort = mockk<ConversationEventPort>(relaxed = true)
    private val eventPublisher = mockk<CommunityEventPublisherPort>(relaxed = true)
    private val conversationQueryPort = mockk<ConversationQueryPort>(relaxed = true)
    private val storage = mockk<ObjectStoragePort> { every { findSize(any()) } returns 1_024L }
    private val clock = mockk<ClockPort> { every { now() } returns now }

    // 출처 없는 대화가 기본이다(SPEC-0026 R13).
    private val spotQaQueryPort = mockk<SpotQaQueryPort>(relaxed = true)
    private val spotLookupPort = mockk<SpotLookupPort>(relaxed = true)

    private val service = ConversationCommandService(
        conversationRepository,
        messageRepository,
        memberLookupPort,
        communityQueryPort,
        conversationEventPort,
        conversationQueryPort,
        spotQaQueryPort,
        spotLookupPort,
        eventPublisher,
        storage,
        clock,
    )

    private fun givenExistingCounterpart(id: Long = authorId) {
        every { memberLookupPort.findAuthor(id) } returns
            PostAuthorView(id = id, nickname = "상대", profileImageUrl = null)
    }

    private fun conversation(low: Long = memberId, high: Long = authorId) = Conversation.restore(
        id = conversationId,
        memberLow = minOf(low, high),
        memberHigh = maxOf(low, high),
        lastMessageAt = now,
        createdAt = Instant.EPOCH,
    )

    private fun givenNoBlocks() {
        every { communityQueryPort.findBlockedMemberIds(any()) } returns emptySet()
    }

    // ── 대화 시작(SPEC-0021 R2) ──────────────────────────────────────

    /** AC2.1 — 상대 회원을 지목하면 대화가 생성된다. */
    @Test
    fun `상대 회원을 지목하면 대화가 시작된다`() {
        givenExistingCounterpart()
        givenNoBlocks()
        every { conversationRepository.saveIfAbsent(any()) } returns conversation()

        val id = service.startConversation(StartConversationCommand(authorId), memberId)

        assertEquals(conversationId, id)
    }

    /** AC2.2 — 기존 대화가 있으면 새로 만들지 않는다(저장소 유일 제약이 판정). */
    @Test
    fun `이미 대화가 있으면 기존 대화가 반환된다`() {
        givenExistingCounterpart()
        givenNoBlocks()
        every { conversationRepository.saveIfAbsent(any()) } returns conversation()

        val id = service.startConversation(StartConversationCommand(authorId), memberId)

        assertEquals(conversationId, id)
        // saveIfAbsent 한 번만 부르고 존재 확인을 따로 하지 않는다.
        verify(exactly = 1) { conversationRepository.saveIfAbsent(any()) }
    }

    /** AC2.3 — 자기 자신은 상대가 될 수 없다. 회원 조회도 하지 않는다. */
    @Test
    fun `자기 자신을 지목하면 SELF_CONVERSATION이다`() {
        assertFailsWith<SelfConversationException> {
            service.startConversation(StartConversationCommand(memberId), memberId)
        }

        verify(exactly = 0) { memberLookupPort.findAuthor(any()) }
        verify(exactly = 0) { conversationRepository.saveIfAbsent(any()) }
    }

    /** AC2.4 — 없는 회원을 지목하면 MEMBER_NOT_FOUND다. */
    @Test
    fun `없는 회원을 지목하면 MEMBER_NOT_FOUND다`() {
        every { memberLookupPort.findAuthor(authorId) } returns null

        assertFailsWith<MemberNotFoundException> {
            service.startConversation(StartConversationCommand(authorId), memberId)
        }

        verify(exactly = 0) { conversationRepository.saveIfAbsent(any()) }
    }

    /** AC2.6 — 탈퇴 회원도 같은 코드(MEMBER_NOT_FOUND)다. */
    @Test
    fun `탈퇴 회원을 지목하면 없는 회원과 같은 MEMBER_NOT_FOUND다`() {
        every { memberLookupPort.findAuthor(authorId) } returns null

        assertFailsWith<MemberNotFoundException> {
            service.startConversation(StartConversationCommand(authorId), memberId)
        }
    }

    /** AC2.5 — 차단 관계면 대화를 시작할 수 없다. 판정은 양방향이다(POL-041 1항). */
    @Test
    fun `차단 관계면 대화 시작이 거부된다`() {
        givenExistingCounterpart()
        every { communityQueryPort.findBlockedMemberIds(memberId) } returns setOf(authorId)

        assertFailsWith<BlockedException> {
            service.startConversation(StartConversationCommand(authorId), memberId)
        }

        verify(exactly = 0) { conversationRepository.saveIfAbsent(any()) }
    }

    // ── 메시지 전송(R9) ──────────────────────────────────────────────

    /** AC9-1 — 메시지가 생성되고 대화의 최근 활동 시각이 갱신된다. */
    @Test
    fun `메시지가 전송되고 대화의 활동 시각이 갱신된다`() {
        every { conversationRepository.findById(conversationId) } returns conversation()
        givenNoBlocks()
        val sentAt = now.plusSeconds(10)
        every { messageRepository.save(any()) } answers {
            val message = firstArg<Message>()
            Message.restore(500L, message.conversationId, message.senderId, message.body, sentAt)
        }

        val messageId = service.sendMessage(SendMessageCommand(conversationId, "안녕"), memberId, language)

        assertEquals(500L, messageId)
        verify { conversationRepository.touch(conversationId, sentAt) }
    }

    /** 저장 뒤 새 메시지 이벤트가 id만 실어 발행된다(R9, POL-013). */
    @Test
    fun `메시지를 저장한 뒤 새 메시지 이벤트를 발행한다`() {
        every { conversationRepository.findById(conversationId) } returns conversation()
        givenNoBlocks()
        every { messageRepository.save(any()) } answers {
            val message = firstArg<Message>()
            Message.restore(500L, message.conversationId, message.senderId, message.body, now)
        }

        service.sendMessage(SendMessageCommand(conversationId, "안녕"), memberId, language)

        // 브로커로 직접 발행하지 않는다 — 커밋 뒤 발행은 리스너의 몫이다.
        verify { eventPublisher.publish(NewMessageStored(conversationId, 500L)) }
        verify(exactly = 0) { conversationEventPort.publishNewMessage(any(), any()) }
    }

    /** 전송이 실패하면 알릴 것도 없다 — 저장 전에 거부되면 이벤트가 나가지 않는다. */
    @Test
    fun `전송이 거부되면 이벤트를 발행하지 않는다`() {
        every { conversationRepository.findById(conversationId) } returns conversation()
        every { communityQueryPort.findBlockedMemberIds(memberId) } returns setOf(authorId)

        assertFailsWith<BlockedException> {
            service.sendMessage(SendMessageCommand(conversationId, "안녕"), memberId, language)
        }

        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    /** upToMessageId를 생략하면 마지막 메시지까지 읽음 처리한다. */
    @Test
    fun `읽을 지점을 주지 않으면 마지막 메시지까지 읽은 것으로 본다`() {
        every { conversationRepository.findById(conversationId) } returns conversation()
        every { conversationQueryPort.findLastMessageId(conversationId) } returns 700L
        every { conversationQueryPort.countUnreadOf(listOf(conversationId), memberId) } returns emptyMap()

        val remaining = service.markConversationRead(MarkConversationReadCommand(conversationId), memberId)

        val saved = slot<Conversation>()
        verify { conversationRepository.updateReadState(capture(saved)) }
        assertEquals(700L, saved.captured.lastReadMessageIdOf(memberId))
        assertEquals(0, remaining)
    }

    /** 메시지가 하나도 없으면 읽을 것이 없다 — 저장도 하지 않는다. */
    @Test
    fun `메시지가 없는 대화는 읽음 처리하지 않는다`() {
        every { conversationRepository.findById(conversationId) } returns conversation()
        every { conversationQueryPort.findLastMessageId(conversationId) } returns null

        val remaining = service.markConversationRead(MarkConversationReadCommand(conversationId), memberId)

        assertEquals(0, remaining)
        verify(exactly = 0) { conversationRepository.updateReadState(any()) }
    }

    /** 처리 후 남은 수를 돌려준다. */
    @Test
    fun `읽음 처리 후 남은 안 읽은 수를 돌려준다`() {
        every { conversationRepository.findById(conversationId) } returns conversation()
        every { conversationQueryPort.countUnreadOf(listOf(conversationId), memberId) } returns
            mapOf(conversationId to 3)

        val remaining = service.markConversationRead(
            MarkConversationReadCommand(conversationId, upToMessageId = 500L),
            memberId,
        )

        assertEquals(3, remaining)
    }

    @Test
    fun `참여자가 아니면 읽음 처리할 수 없다`() {
        every { conversationRepository.findById(conversationId) } returns conversation()

        assertFailsWith<NotConversationParticipantException> {
            service.markConversationRead(MarkConversationReadCommand(conversationId), 999L)
        }
    }

    /** AC9-4 — 참여자가 아닌 **기존** 대화에는 전송할 수 없다. */
    @Test
    fun `참여자가 아니면 NOT_CONVERSATION_PARTICIPANT다`() {
        every { conversationRepository.findById(conversationId) } returns conversation(low = 100L, high = 200L)

        assertFailsWith<NotConversationParticipantException> {
            service.sendMessage(SendMessageCommand(conversationId, "안녕"), memberId, language)
        }
    }

    /** AC9-5 — 없는 대화와 권한 없는 대화는 서로 다른 코드다. */
    @Test
    fun `없는 대화는 CONVERSATION_NOT_FOUND로 구분된다`() {
        every { conversationRepository.findById(conversationId) } returns null

        assertFailsWith<ConversationNotFoundException> {
            service.sendMessage(SendMessageCommand(conversationId, "안녕"), memberId, language)
        }
    }

    /** AC11-4 — 대화 성립 이후의 차단도 전송을 막는다. */
    @Test
    fun `대화 성립 이후 차단되면 전송이 거부된다`() {
        every { conversationRepository.findById(conversationId) } returns conversation()
        every { communityQueryPort.findBlockedMemberIds(memberId) } returns setOf(authorId)

        assertFailsWith<BlockedException> {
            service.sendMessage(SendMessageCommand(conversationId, "안녕"), memberId, language)
        }

        verify(exactly = 0) { messageRepository.save(any()) }
    }

    /** AC9-2 — 공백만인 본문은 도메인이 거부한다. */
    @Test
    fun `공백만인 본문은 거부된다`() {
        every { conversationRepository.findById(conversationId) } returns conversation()
        givenNoBlocks()

        assertFailsWith<IllegalArgumentException> {
            service.sendMessage(SendMessageCommand(conversationId, "   "), memberId, language)
        }
    }

    /** AC9-3 — 상한을 넘으면 잘라 저장하지 않는다. */
    @Test
    fun `상한을 넘는 본문은 잘리지 않고 거부된다`() {
        every { conversationRepository.findById(conversationId) } returns conversation()
        givenNoBlocks()

        assertFailsWith<IllegalArgumentException> {
            service.sendMessage(
                SendMessageCommand(conversationId, "가".repeat(Message.BODY_MAX_LENGTH + 1)),
                memberId,
                language,
            )
        }

        verify(exactly = 0) { messageRepository.save(any()) }
    }

    // ── 타이핑 알림(R10) ─────────────────────────────────────────────

    /** AC10-2 — 수신자가 구독 중이 아니어도 성공한다. */
    @Test
    fun `수신자가 없어도 타이핑 알림은 성공한다`() {
        every { conversationRepository.findById(conversationId) } returns conversation()

        service.notifyTyping(NotifyTypingCommand(conversationId), memberId)

        verify { conversationEventPort.publishTyping(match { it.conversationId == conversationId && it.senderId == memberId }) }
    }

    /** AC10-3 계열 — 뮤테이션이므로 참여자 실패는 userErrors 쪽 코드다. */
    @Test
    fun `참여자가 아니면 타이핑 알림이 거부된다`() {
        every { conversationRepository.findById(conversationId) } returns conversation(low = 100L, high = 200L)

        assertFailsWith<NotConversationParticipantException> {
            service.notifyTyping(NotifyTypingCommand(conversationId), memberId)
        }

        verify(exactly = 0) { conversationEventPort.publishTyping(any()) }
    }

    /** AC10-5 — 대화가 없는 것과 받을 사람이 없는 것은 다르다. */
    @Test
    fun `없는 대화의 타이핑 알림은 CONVERSATION_NOT_FOUND다`() {
        every { conversationRepository.findById(conversationId) } returns null

        assertFailsWith<ConversationNotFoundException> {
            service.notifyTyping(NotifyTypingCommand(conversationId), memberId)
        }
    }
}
