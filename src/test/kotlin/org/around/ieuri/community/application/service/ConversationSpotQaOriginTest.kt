package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.around.ieuri.community.application.command.StartConversationCommand
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
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.domain.aggregate.Conversation
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * DM 출처 스냅샷 생성(SPEC-0026 R13, R14).
 *
 * 핵심은 **출처를 만들지 못해도 대화는 열린다**는 것이다(AC-13.4) — 거절하지 않는다.
 * 그리고 스펙에 없던 방어 하나: 출처 답변의 작성자가 대화 상대가 아니면 무시한다.
 * 없으면 남의 답변을 출처로 붙여 상대의 받은함에 맥락을 위조할 수 있다.
 */
class ConversationSpotQaOriginTest {

    private val memberId = 7L
    private val counterpartId = 20L
    private val answerId = 9L
    private val catalogId = 11L
    private val conversationId = 77L
    private val now = Instant.parse("2026-08-18T00:00:00Z")

    private val conversationRepository = mockk<ConversationRepositoryPort>()
    private val messageRepository = mockk<MessageRepositoryPort>(relaxed = true)
    private val memberLookupPort = mockk<MemberLookupPort>()
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

    private val saved = slot<Conversation>()

    private fun givenCounterpartExists() {
        every { memberLookupPort.findAuthor(counterpartId) } returns
            PostAuthorView(id = counterpartId, nickname = "상대", profileImageUrl = null)
        every { communityQueryPort.findBlockedMemberIds(memberId) } returns emptySet()
        every { conversationRepository.saveIfAbsent(capture(saved)) } answers {
            firstArg<Conversation>().withId(conversationId)
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
        every { spotLookupPort.findSummary(catalogId) } returns
            if (exists) {
                PostSpotView(
                    id = catalogId, title = title, thumbnailImageUrl = "https://cdn/x.jpg",
                    category = null, longitude = 127.0, latitude = 37.5,
                )
            } else {
                null
            }
    }

    private fun startWithSource(sourceAnswerId: Long? = answerId) =
        service.startConversation(
            StartConversationCommand(counterpartMemberId = counterpartId, sourceAnswerId = sourceAnswerId),
            memberId,
        )

    @Test
    fun `출처를 실으면 스냅샷이 대화에 남는다`() {
        givenCounterpartExists()
        givenOriginSource()
        givenSpotExists()

        assertEquals(conversationId, startWithSource())

        val origin = requireNotNull(saved.captured.spotQaOrigin)
        assertEquals(catalogId, origin.catalogId)
        assertEquals("경복궁", origin.spotTitle)
        assertEquals(2L, origin.questionId)
        assertEquals("웨이팅 있나요?", origin.questionExcerpt)
        assertEquals(answerId, origin.answerId)
        assertEquals("지금은 없어요", origin.answerExcerpt)
    }

    @Test
    fun `출처 없이 시작하면 스냅샷이 비어 있다`() {
        givenCounterpartExists()

        assertEquals(conversationId, startWithSource(sourceAnswerId = null))

        assertNull(saved.captured.spotQaOrigin)
    }

    /** AC-13.4 — 거절하지 않는다. 대화를 여는 것이 본질이고 출처는 부가 맥락이다. */
    @Test
    fun `출처 답변이 이미 삭제됐으면 출처 없이 대화가 열린다`() {
        givenCounterpartExists()
        every { spotQaQueryPort.findAnswerOriginSource(answerId) } returns null

        assertEquals(conversationId, startWithSource())

        assertNull(saved.captured.spotQaOrigin)
    }

    @Test
    fun `장소가 사라졌으면 출처 없이 대화가 열린다`() {
        givenCounterpartExists()
        givenOriginSource()
        givenSpotExists(exists = false)

        assertEquals(conversationId, startWithSource())

        assertNull(saved.captured.spotQaOrigin)
    }

    /**
     * 스펙에 없던 방어. 이것이 빠지면 A가 C의 답변을 출처로 B에게 DM을 열어, B의 받은함에
     * "B의 답변에서 시작된 대화"인 것처럼 보이는 맥락을 만들 수 있다.
     */
    @Test
    fun `출처 답변의 작성자가 대화 상대가 아니면 출처를 무시한다`() {
        givenCounterpartExists()
        givenOriginSource(authorId = counterpartId + 1)
        givenSpotExists()

        assertEquals(conversationId, startWithSource())

        assertNull(saved.captured.spotQaOrigin)
    }

    /** AC-14.4 — 발췌는 100자로 자른다. */
    @Test
    fun `긴 본문은 발췌 길이로 잘린다`() {
        givenCounterpartExists()
        givenOriginSource(questionBody = "가".repeat(501), answerBody = "나".repeat(300))
        givenSpotExists()

        startWithSource()

        val origin = requireNotNull(saved.captured.spotQaOrigin)
        assertEquals(100, origin.questionExcerpt.length)
        assertEquals(100, origin.answerExcerpt.length)
    }
}
