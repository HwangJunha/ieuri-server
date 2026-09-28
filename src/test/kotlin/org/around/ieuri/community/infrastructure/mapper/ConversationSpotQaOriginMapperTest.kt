package org.around.ieuri.community.infrastructure.mapper

import org.around.ieuri.community.application.query.ConversationSpotQaOriginView
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.vo.SpotQaOrigin
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper.toGql
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageQuoteEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageQuoteType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toQuoteEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toSpotQaOrigin
import org.around.ieuri.community.infrastructure.adapter.out.query.mapper.toSpotQaOriginView
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 메시지 인용 스냅샷의 변환 세 구간(SPEC-0026 R14).
 * - 필드마다 다른 값을 쓴다. 같은 타입 필드가 뒤바뀌면 잡아낸다.
 */
class ConversationSpotQaOriginMapperTest {

    private val createdAt = Instant.parse("2026-08-18T01:02:03Z")

    /**
     * 장소 답변 인용의 자리 배치. title=장소명, primary=질문, secondary=답변,
     * context=카탈로그, parent=질문 id, target=답변 id다.
     */
    private fun quoteEntity() = MessageQuoteEntity(
        id = 1L,
        messageId = 7L,
        quoteType = MessageQuoteType.SPOT_ANSWER,
        targetId = 63L,
        titleSnapshot = "경복궁",
        primaryExcerptEncrypted = "암호문-질문",
        secondaryExcerptEncrypted = "암호문-답변",
        contextId = 41L,
        parentId = 52L,
        createdAt = createdAt,
    )

    // ── 엔티티 → 도메인 ───────────────────────────────────────────

    @Test
    fun `인용 엔티티가 도메인 값으로 필드별로 옮겨진다`() {
        val origin = quoteEntity().toSpotQaOrigin(
            decryptedPrimaryExcerpt = "질문 평문",
            decryptedSecondaryExcerpt = "답변 평문",
        )

        assertEquals(41L, origin.catalogId)
        assertEquals("경복궁", origin.spotTitle)
        assertEquals(52L, origin.questionId)
        assertEquals("질문 평문", origin.questionExcerpt)
        assertEquals(63L, origin.answerId)
        assertEquals("답변 평문", origin.answerExcerpt)
    }

    /** 인용 행이 없으면 메시지에 인용이 없다. */
    @Test
    fun `인용을 넘기지 않으면 메시지의 인용이 null이다`() {
        val entity = MessageEntity(
            id = 1L,
            conversationId = 2L,
            senderId = 3L,
            bodyEncrypted = "암호화된-본문",
            createdAt = createdAt,
        )

        assertNull(entity.toDomain(decryptedBody = "본문").spotQaOrigin)
    }

    // ── 도메인 → 엔티티 ───────────────────────────────────────────

    @Test
    fun `도메인 인용이 새 엔티티로 필드별로 옮겨진다`() {
        val message = Message.create(
            conversationId = 2L,
            senderId = 3L,
            body = "이것도 궁금해요",
            spotQaOrigin = SpotQaOrigin(
                catalogId = 41L,
                spotTitle = "경복궁",
                questionId = 52L,
                questionExcerpt = "질문 평문",
                answerId = 63L,
                answerExcerpt = "답변 평문",
            ),
        )

        val entity = requireNotNull(message.spotQaOrigin).toQuoteEntity(
            messageId = 7L,
            encryptedQuestionExcerpt = "암호문-질문",
            encryptedAnswerExcerpt = "암호문-답변",
        )

        assertEquals(7L, entity.messageId)
        assertEquals(MessageQuoteType.SPOT_ANSWER, entity.quoteType)
        assertEquals(63L, entity.targetId)
        assertEquals("경복궁", entity.titleSnapshot)
        assertEquals("암호문-질문", entity.primaryExcerptEncrypted)
        assertEquals("암호문-답변", entity.secondaryExcerptEncrypted)
        assertEquals(41L, entity.contextId)
        assertEquals(52L, entity.parentId)
    }

    // ── 엔티티 → 읽기 모델 ────────────────────────────────────────

    @Test
    fun `엔티티가 읽기 모델로 필드별로 옮겨진다`() {
        val view = quoteEntity().toSpotQaOriginView(
            decryptedPrimaryExcerpt = "질문 평문",
            decryptedSecondaryExcerpt = "답변 평문",
        )

        assertEquals(41L, view.catalogId)
        assertEquals("경복궁", view.spotTitle)
        assertEquals(52L, view.questionId)
        assertEquals("질문 평문", view.questionBodySnapshot)
        assertEquals(63L, view.answerId)
        assertEquals("답변 평문", view.answerBodySnapshot)
    }

    // ── 읽기 모델 → GraphQL ───────────────────────────────────────

    @Test
    fun `읽기 모델이 응답 타입으로 필드별로 옮겨진다`() {
        val gql = ConversationSpotQaOriginView(
            catalogId = 41L,
            spotTitle = "경복궁",
            questionId = 52L,
            questionBodySnapshot = "질문 평문",
            answerId = 63L,
            answerBodySnapshot = "답변 평문",
        ).toGql()

        assertEquals(41L, gql.catalogId)
        assertEquals("경복궁", gql.spotTitle)
        assertEquals(52L, gql.questionId)
        assertEquals("질문 평문", gql.questionBodySnapshot)
        assertEquals(63L, gql.answerId)
        assertEquals("답변 평문", gql.answerBodySnapshot)
    }
}
