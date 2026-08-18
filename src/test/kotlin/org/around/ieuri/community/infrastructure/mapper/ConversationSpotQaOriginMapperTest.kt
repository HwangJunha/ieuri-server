package org.around.ieuri.community.infrastructure.mapper

import org.around.ieuri.community.application.query.ConversationSpotQaOriginView
import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.vo.SpotQaOrigin
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper.toGql
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.community.infrastructure.adapter.out.query.mapper.toSpotQaOriginView
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * DM 출처 스냅샷의 변환 세 구간(SPEC-0026 R14).
 *
 * **필드마다 다른 값을 쓴다.** 여섯 필드 중 셋이 `Long`, 셋이 `String`이라 값을 공유하면
 * 뒤바뀌어도 통과한다 — 질문 발췌 자리에 답변 발췌가 실리면 맥락 카드가 영구히 틀린 값을
 * 보여준다. 스냅샷은 대화 생성 시 한 번 쓰이고 다시 만들어지지 않기 때문이다.
 */
class ConversationSpotQaOriginMapperTest {

    private val createdAt = Instant.parse("2026-08-18T01:02:03Z")
    private val lastMessageAt = Instant.parse("2026-08-19T04:05:06Z")

    private fun entityWithOrigin() = ConversationEntity(
        id = 1L,
        memberLow = 2L,
        memberHigh = 3L,
        lastMessageAt = lastMessageAt,
        createdAt = createdAt,
        spotQaCatalogId = 41L,
        spotQaSpotTitle = "경복궁",
        spotQaQuestionId = 52L,
        spotQaQuestionBodyEncrypted = "암호문-질문",
        spotQaAnswerId = 63L,
        spotQaAnswerBodyEncrypted = "암호문-답변",
    )

    // ── 엔티티 → 도메인 ───────────────────────────────────────────

    @Test
    fun `엔티티가 도메인 출처로 필드별로 옮겨진다`() {
        val conversation = entityWithOrigin().toDomain(
            decryptedQuestionExcerpt = "질문 평문",
            decryptedAnswerExcerpt = "답변 평문",
        )

        val origin = assertNotNull(conversation.spotQaOrigin)
        assertEquals(41L, origin.catalogId)
        assertEquals("경복궁", origin.spotTitle)
        assertEquals(52L, origin.questionId)
        assertEquals("질문 평문", origin.questionExcerpt)
        assertEquals(63L, origin.answerId)
        assertEquals("답변 평문", origin.answerExcerpt)
    }

    @Test
    fun `출처 컬럼이 비어 있으면 도메인 출처가 null이다`() {
        val entity = ConversationEntity(
            id = 1L, memberLow = 2L, memberHigh = 3L,
            lastMessageAt = lastMessageAt, createdAt = createdAt,
        )

        assertNull(entity.toDomain().spotQaOrigin)
    }

    /** 복호화 결과가 없으면 출처가 성립하지 않는다 — 발췌 없는 맥락 카드는 그릴 수 없다. */
    @Test
    fun `발췌 평문이 없으면 도메인 출처가 null이다`() {
        val conversation = entityWithOrigin().toDomain(
            decryptedQuestionExcerpt = null,
            decryptedAnswerExcerpt = null,
        )

        assertNull(conversation.spotQaOrigin)
    }

    // ── 도메인 → 엔티티 ───────────────────────────────────────────

    @Test
    fun `도메인 출처가 새 엔티티로 필드별로 옮겨진다`() {
        val conversation = Conversation.create(
            memberId = 2L,
            counterpartId = 3L,
            now = createdAt,
            spotQaOrigin = SpotQaOrigin(
                catalogId = 41L,
                spotTitle = "경복궁",
                questionId = 52L,
                questionExcerpt = "질문 평문",
                answerId = 63L,
                answerExcerpt = "답변 평문",
            ),
        )

        val entity = conversation.toNewEntity(
            encryptedQuestionExcerpt = "암호문-질문",
            encryptedAnswerExcerpt = "암호문-답변",
        )

        assertEquals(41L, entity.spotQaCatalogId)
        assertEquals("경복궁", entity.spotQaSpotTitle)
        assertEquals(52L, entity.spotQaQuestionId)
        assertEquals("암호문-질문", entity.spotQaQuestionBodyEncrypted)
        assertEquals(63L, entity.spotQaAnswerId)
        assertEquals("암호문-답변", entity.spotQaAnswerBodyEncrypted)
    }

    /** 출처 없는 일반 DM은 여섯 컬럼이 전부 비어야 CHECK를 만족한다. */
    @Test
    fun `출처 없는 대화는 여섯 컬럼이 모두 비어 있다`() {
        val entity = Conversation.create(memberId = 2L, counterpartId = 3L, now = createdAt)
            .toNewEntity()

        assertNull(entity.spotQaCatalogId)
        assertNull(entity.spotQaSpotTitle)
        assertNull(entity.spotQaQuestionId)
        assertNull(entity.spotQaQuestionBodyEncrypted)
        assertNull(entity.spotQaAnswerId)
        assertNull(entity.spotQaAnswerBodyEncrypted)
    }

    // ── 엔티티 → 읽기 모델 ────────────────────────────────────────

    @Test
    fun `엔티티가 읽기 모델로 필드별로 옮겨진다`() {
        val view = assertNotNull(
            entityWithOrigin().toSpotQaOriginView(
                decryptedQuestionExcerpt = "질문 평문",
                decryptedAnswerExcerpt = "답변 평문",
            ),
        )

        assertEquals(41L, view.catalogId)
        assertEquals("경복궁", view.spotTitle)
        assertEquals(52L, view.questionId)
        assertEquals("질문 평문", view.questionBodySnapshot)
        assertEquals(63L, view.answerId)
        assertEquals("답변 평문", view.answerBodySnapshot)
    }

    @Test
    fun `출처 컬럼이 비면 읽기 모델도 null이다`() {
        val entity = ConversationEntity(
            id = 1L, memberLow = 2L, memberHigh = 3L,
            lastMessageAt = lastMessageAt, createdAt = createdAt,
        )

        assertNull(entity.toSpotQaOriginView(null, null))
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
