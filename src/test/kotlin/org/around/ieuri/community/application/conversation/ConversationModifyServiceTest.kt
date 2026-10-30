package org.around.ieuri.community.application.conversation

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.community.application.block.provided.BlockQueryUseCase
import org.around.ieuri.community.application.conversation.provided.dto.MarkConversationReadCommand
import org.around.ieuri.community.application.conversation.provided.dto.SendMessageCommand
import org.around.ieuri.community.application.conversation.provided.dto.StartConversationCommand
import org.around.ieuri.community.application.conversation.provided.exception.BlockedException
import org.around.ieuri.community.application.conversation.provided.exception.ConversationNotFoundException
import org.around.ieuri.community.application.conversation.provided.exception.PhotoNotConfirmedException
import org.around.ieuri.community.application.conversation.required.ConversationEventPort
import org.around.ieuri.community.application.conversation.required.ConversationEventPublisherPort
import org.around.ieuri.community.application.conversation.required.ConversationRepository
import org.around.ieuri.community.application.lookup.provided.MemberLookupUseCase
import org.around.ieuri.community.application.lookup.provided.SpotLookupUseCase
import org.around.ieuri.community.application.photo.UploadedPhotoService
import org.around.ieuri.community.application.photo.required.ObjectStoragePort
import org.around.ieuri.community.application.question.provided.SpotQuestionQueryUseCase
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerOriginView
import org.around.ieuri.community.application.shared.exception.MemberNotFoundException
import org.around.ieuri.community.domain.conversation.Conversation
import org.around.ieuri.community.domain.conversation.Message
import org.around.ieuri.community.domain.conversation.NotConversationParticipantException
import org.around.ieuri.community.domain.conversation.SelfConversationException
import org.around.ieuri.community.domain.conversation.event.MessageSent
import org.around.ieuri.community.domain.lookup.MemberSummary
import org.around.ieuri.community.domain.lookup.SpotSummary
import org.around.ieuri.community.domain.photo.PhotoObjectKey
import org.around.ieuri.community.domain.shared.ContentLanguage
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ConversationModifyServiceTest {

    private val now = Instant.parse("2026-10-05T00:00:00Z")
    private val conversations = mockk<ConversationRepository>(relaxUnitFun = true)
    private val blocks = mockk<BlockQueryUseCase>()
    private val questions = mockk<SpotQuestionQueryUseCase>()
    private val members = mockk<MemberLookupUseCase>()
    private val spots = mockk<SpotLookupUseCase>()
    private val storage = mockk<ObjectStoragePort>()
    private val conversationEvents = mockk<ConversationEventPort>(relaxUnitFun = true)
    private val events = mockk<ConversationEventPublisherPort>(relaxUnitFun = true)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val service = ConversationModifyService(conversations, blocks, questions, members, spots, UploadedPhotoService(storage), conversationEvents, events, clock)

    private val conversation = Conversation(5, memberLow = 7, memberHigh = 9, lastMessageAt = now, lastReadMessageIdLow = null, lastReadMessageIdHigh = null)

    private fun stored(message: Message) = Message(201, message.conversationId, message.senderId, message.body, message.photoKey, message.spotQaOrigin, now)

    @Test
    fun `자기 자신과의 대화는 회원 조회보다 먼저 거부한다`() {
        assertFailsWith<SelfConversationException> { service.startConversation(StartConversationCommand(7), memberId = 7) }
        verify(exactly = 0) { members.member(any()) }
    }

    @Test
    fun `없는 회원은 MEMBER_NOT_FOUND, 차단 관계면 BLOCKED이고 둘 다 저장하지 않는다`() {
        every { members.member(9) } returns null
        assertFailsWith<MemberNotFoundException> { service.startConversation(StartConversationCommand(9), 7) }

        every { members.member(9) } returns MemberSummary(9, "상대", null)
        every { blocks.isBlockedBetween(7, 9) } returns true
        assertFailsWith<BlockedException> { service.startConversation(StartConversationCommand(9), 7) }

        verify(exactly = 0) { conversations.insertIfAbsent(any()) }
    }

    @Test
    fun `대화 시작은 정렬된 쌍으로 저장하고 기존 대화면 그 id다`() {
        every { members.member(7) } returns MemberSummary(7, "나", null)
        every { blocks.isBlockedBetween(9, 7) } returns false
        val pair = slot<Conversation>()
        every { conversations.insertIfAbsent(capture(pair)) } returns 5

        assertEquals(5L, service.startConversation(StartConversationCommand(7), memberId = 9))
        assertEquals(7L, pair.captured.memberLow)
        assertEquals(9L, pair.captured.memberHigh)
    }

    @Test
    fun `전송은 최근 활동 시각을 메시지 시각으로 올리고 알림·브로드캐스트 이벤트를 낸다`() {
        every { conversations.findById(5) } returns conversation
        every { blocks.isBlockedBetween(7, 9) } returns false
        every { conversations.insertMessage(any()) } answers { stored(firstArg()) }

        assertEquals(201L, service.sendMessage(SendMessageCommand(5, "안녕"), memberId = 7, language = ContentLanguage.KO))

        verify { conversations.advanceLastMessageAt(5, now) }
        verify { events.publish(MessageSent(conversationId = 5, messageId = 201, senderId = 7, recipientId = 9)) }
        verify { events.publish(NewMessageStored(5, 201)) }
    }

    @Test
    fun `대화가 없으면 CONVERSATION_NOT_FOUND, 참여자가 아니면 NOT_CONVERSATION_PARTICIPANT, 차단이면 BLOCKED다`() {
        every { conversations.findById(6) } returns null
        assertFailsWith<ConversationNotFoundException> { service.sendMessage(SendMessageCommand(6, "x"), 7, ContentLanguage.KO) }

        every { conversations.findById(5) } returns conversation
        assertFailsWith<NotConversationParticipantException> { service.sendMessage(SendMessageCommand(5, "x"), 8, ContentLanguage.KO) }

        every { blocks.isBlockedBetween(7, 9) } returns true
        assertFailsWith<BlockedException> { service.sendMessage(SendMessageCommand(5, "x"), 7, ContentLanguage.KO) }

        verify(exactly = 0) { conversations.insertMessage(any()) }
    }

    @Test
    fun `사진 키 확인은 대화를 읽기 전에 하고 실패는 PHOTO_NOT_CONFIRMED 하나다`() {
        val key = PhotoObjectKey.issue(7, UUID.randomUUID()).toConfirmed().value
        every { storage.findSize(key) } returns null

        assertFailsWith<PhotoNotConfirmedException> { service.sendMessage(SendMessageCommand(5, "", photoKey = key), 7, ContentLanguage.KO) }
        assertFailsWith<PhotoNotConfirmedException> { service.sendMessage(SendMessageCommand(5, "", photoKey = "bad"), 7, ContentLanguage.KO) }
        verify(exactly = 0) { conversations.findById(any()) }
    }

    @Test
    fun `인용은 상대가 쓴 답변일 때만 붙고 아니면 인용 없이 보낸다`() {
        every { conversations.findById(5) } returns conversation
        every { blocks.isBlockedBetween(7, 9) } returns false
        every { questions.answerOrigin(20) } returns SpotAnswerOriginView(answerId = 20, answerAuthorId = 9, answerBody = "답변", questionId = 1, questionBody = "질문", catalogId = 343259)
        every { questions.answerOrigin(21) } returns SpotAnswerOriginView(answerId = 21, answerAuthorId = 8, answerBody = "남의 답변", questionId = 1, questionBody = "질문", catalogId = 343259)
        every { spots.spot(343259, ContentLanguage.KO) } returns SpotSummary(343259, "남산", "", null, 127.0, 37.5)
        val sent = mutableListOf<Message>()
        every { conversations.insertMessage(capture(sent)) } answers { stored(firstArg()) }

        service.sendMessage(SendMessageCommand(5, "이거요", sourceAnswerId = 20), 7, ContentLanguage.KO)
        service.sendMessage(SendMessageCommand(5, "이거요", sourceAnswerId = 21), 7, ContentLanguage.KO)

        val origin = sent[0].spotQaOrigin
        assertEquals(343259L, origin?.catalogId)
        assertEquals("남산", origin?.spotTitle)
        assertEquals(1L, origin?.questionId)
        assertEquals("질문", origin?.questionExcerpt)
        assertEquals(20L, origin?.answerId)
        assertEquals("답변", origin?.answerExcerpt)
        assertNull(sent[1].spotQaOrigin)
    }

    @Test
    fun `읽음은 지정한 메시지(없으면 마지막)까지 올리고 남은 안 읽은 수를 돌려준다`() {
        every { conversations.findById(5) } returns conversation
        every { conversations.lastMessageId(5) } returns 300
        every { conversations.countUnread(5, 7, 300) } returns 0

        assertEquals(0, service.markConversationRead(MarkConversationReadCommand(5), memberId = 7))
        verify { conversations.advanceReadPoints(conversation.copy(lastReadMessageIdLow = 300)) }
    }

    @Test
    fun `메시지가 없는 대화의 읽음은 0이고 아무것도 쓰지 않는다`() {
        every { conversations.findById(5) } returns conversation
        every { conversations.lastMessageId(5) } returns null

        assertEquals(0, service.markConversationRead(MarkConversationReadCommand(5), 7))
        verify(exactly = 0) { conversations.advanceReadPoints(any()) }
    }

    @Test
    fun `탈퇴 정리는 대화마다 참여자를 확인하고 그 대화의 보낸 메시지를 지운다`() {
        every { conversations.findIdsByParticipant(7) } returns listOf(5, 6)
        every { conversations.findById(5) } returns conversation
        every { conversations.findById(6) } returns null
        every { conversations.markMessagesDeletedBySender(5, 7, now) } returns 4

        assertEquals(4, service.eraseMemberContent(7, now))
        verify(exactly = 0) { conversations.markMessagesDeletedBySender(6, any(), any()) }
    }

    @Test
    fun `인용할 답변이나 질문이 사라졌으면 인용 없이 보낸다`() {
        every { conversations.findById(5) } returns conversation
        every { blocks.isBlockedBetween(7, 9) } returns false
        every { questions.answerOrigin(22) } returns null
        val sent = slot<Message>()
        every { conversations.insertMessage(capture(sent)) } answers { stored(firstArg()) }

        service.sendMessage(SendMessageCommand(5, "이거요", sourceAnswerId = 22), 7, ContentLanguage.KO)

        assertNull(sent.captured.spotQaOrigin)
        verify(exactly = 0) { spots.spot(any(), any()) }
    }
}
