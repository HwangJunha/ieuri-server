package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.around.ieuri.community.application.command.SendMessageCommand
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
import org.around.ieuri.community.application.port.out.query.SpotQaOriginSource
import org.around.ieuri.community.application.port.out.query.SpotQaQueryPort
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.vo.ContentLanguage
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 메시지의 장소 질문/답변 인용(SPEC-0026 R13·R14). 인용은 대화가 아니라 메시지에 붙는다.
 * - 인용을 만들지 못해도 메시지는 전송된다(AC-13.4).
 * - 인용 답변의 작성자가 대화 상대가 아니면 인용을 무시한다.
 */
class ConversationSpotQaOriginTest {

    private val memberId = 7L
    private val counterpartId = 20L
    private val conversationId = 77L
    private val answerId = 9L
    private val catalogId = 11L
    private val messageId = 31L
    private val now = Instant.parse("2026-08-18T00:00:00Z")
    private val language = ContentLanguage.EN

    private val conversationRepository = mockk<ConversationRepositoryPort>(relaxed = true)
    private val messageRepository = mockk<MessageRepositoryPort>()
    private val memberLookupPort = mockk<MemberLookupPort>(relaxed = true)
    private val communityQueryPort = mockk<CommunityQueryPort>()
    private val conversationEventPort = mockk<ConversationEventPort>(relaxed = true)
    private val conversationQueryPort = mockk<ConversationQueryPort>(relaxed = true)
    private val spotQaQueryPort = mockk<SpotQaQueryPort>()
    private val spotLookupPort = mockk<SpotLookupPort>()
    private val eventPublisher = mockk<CommunityEventPublisherPort>(relaxed = true)
    private val storage = mockk<ObjectStoragePort>(relaxed = true)
    private val clock = mockk<ClockPort> { every { now() } returns now }

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

    private val saved = slot<Message>()

    private fun givenParticipatingConversation() {
        every { conversationRepository.findById(conversationId) } returns Conversation.restore(
            id = conversationId,
            memberLow = minOf(memberId, counterpartId),
            memberHigh = maxOf(memberId, counterpartId),
            lastMessageAt = now,
            createdAt = now,
        )
        every { communityQueryPort.findBlockedMemberIds(memberId) } returns emptySet()
        every { messageRepository.save(capture(saved)) } answers {
            val m = firstArg<Message>()
            Message.restore(
                id = messageId,
                conversationId = m.conversationId,
                senderId = m.senderId,
                body = m.body,
                createdAt = now,
                photoKey = m.photoKey,
                spotQaOrigin = m.spotQaOrigin,
            )
        }
    }

    private fun givenOriginSource(
        authorId: Long = counterpartId,
        questionBody: String = "웨이팅 있나요?",
        answerBody: String = "지금은 없어요",
    ) {
        every { spotQaQueryPort.findAnswerOriginSource(answerId) } returns SpotQaOriginSource(
            catalogId = catalogId,
            questionId = 2L,
            questionBody = questionBody,
            answerId = answerId,
            answerBody = answerBody,
            answerAuthorId = authorId,
        )
    }

    private fun givenSpotExists(title: String = "경복궁", exists: Boolean = true) {
        every { spotLookupPort.findSummary(catalogId, language) } returns
            if (exists) {
                PostSpotView(
                    id = catalogId,
                    title = title,
                    thumbnailImageUrl = "https://cdn/x.jpg",
                    category = null,
                    longitude = 127.0,
                    latitude = 37.5,
                )
            } else {
                null
            }
    }

    private fun send(body: String = "이것도 궁금해요", sourceAnswerId: Long? = answerId) = service.sendMessage(
        SendMessageCommand(
            conversationId = conversationId,
            body = body,
            sourceAnswerId = sourceAnswerId,
        ),
        memberId,
        language,
    )

    @Test
    fun `인용을 실으면 스냅샷이 메시지에 남는다`() {
        givenParticipatingConversation()
        givenOriginSource()
        givenSpotExists()

        assertEquals(messageId, send())

        val origin = requireNotNull(saved.captured.spotQaOrigin)
        assertEquals(catalogId, origin.catalogId)
        assertEquals("경복궁", origin.spotTitle)
        assertEquals(2L, origin.questionId)
        assertEquals("웨이팅 있나요?", origin.questionExcerpt)
        assertEquals(answerId, origin.answerId)
        assertEquals("지금은 없어요", origin.answerExcerpt)
    }

    @Test
    fun `인용 없이 보내면 스냅샷이 비어 있다`() {
        givenParticipatingConversation()

        assertEquals(messageId, send(sourceAnswerId = null))

        assertNull(saved.captured.spotQaOrigin)
    }

    /** 한 대화에서 여러 질문을 인용할 수 있다. */
    @Test
    fun `같은 대화에서 다른 질문을 다시 인용할 수 있다`() {
        givenParticipatingConversation()
        givenOriginSource(questionBody = "첫 질문")
        givenSpotExists()
        send()
        assertEquals("첫 질문", requireNotNull(saved.captured.spotQaOrigin).questionExcerpt)

        val secondAnswerId = 40L
        every { spotQaQueryPort.findAnswerOriginSource(secondAnswerId) } returns SpotQaOriginSource(
            catalogId = catalogId,
            questionId = 3L,
            questionBody = "둘째 질문",
            answerId = secondAnswerId,
            answerBody = "둘째 답변",
            answerAuthorId = counterpartId,
        )

        send(sourceAnswerId = secondAnswerId)

        val second = requireNotNull(saved.captured.spotQaOrigin)
        assertEquals(3L, second.questionId)
        assertEquals("둘째 질문", second.questionExcerpt)
    }

    /** AC-13.4 — 인용을 만들지 못해도 거절하지 않는다. */
    @Test
    fun `인용 답변이 이미 삭제됐으면 인용 없이 전송된다`() {
        givenParticipatingConversation()
        every { spotQaQueryPort.findAnswerOriginSource(answerId) } returns null

        assertEquals(messageId, send())

        assertNull(saved.captured.spotQaOrigin)
    }

    @Test
    fun `장소가 사라졌으면 인용 없이 전송된다`() {
        givenParticipatingConversation()
        givenOriginSource()
        givenSpotExists(exists = false)

        assertEquals(messageId, send())

        assertNull(saved.captured.spotQaOrigin)
    }

    /** 대화 상대가 쓰지 않은 답변은 인용하지 않는다(맥락 위조 방지). */
    @Test
    fun `인용 답변의 작성자가 대화 상대가 아니면 인용을 무시한다`() {
        givenParticipatingConversation()
        givenOriginSource(authorId = counterpartId + 1)
        givenSpotExists()

        assertEquals(messageId, send())

        assertNull(saved.captured.spotQaOrigin)
    }

    /** AC-14.4 — 발췌는 100자로 자른다. */
    @Test
    fun `긴 본문은 발췌 길이로 잘린다`() {
        givenParticipatingConversation()
        givenOriginSource(questionBody = "가".repeat(501), answerBody = "나".repeat(300))
        givenSpotExists()

        send()

        val origin = requireNotNull(saved.captured.spotQaOrigin)
        assertEquals(100, origin.questionExcerpt.length)
        assertEquals(100, origin.answerExcerpt.length)
    }

    /** 본문 없이 인용만 보내는 것도 성립한다. */
    @Test
    fun `본문 없이 인용만 보낼 수 있다`() {
        givenParticipatingConversation()
        givenOriginSource()
        givenSpotExists()

        assertEquals(messageId, send(body = ""))

        assertNull(saved.captured.body.ifEmpty { null })
        assertEquals(2L, requireNotNull(saved.captured.spotQaOrigin).questionId)
    }
}
