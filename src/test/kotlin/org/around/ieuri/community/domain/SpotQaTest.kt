package org.around.ieuri.community.domain

import org.around.ieuri.community.domain.aggregate.SpotAnswer
import org.around.ieuri.community.domain.aggregate.SpotAnswerReply
import org.around.ieuri.community.domain.aggregate.SpotQuestion
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.SpotQaOrigin
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 장소 질문 불변식(SPEC-0026 R3, R8, R9, R10). */
class SpotQuestionTest {

    private val authorId = 3L
    private val catalogId = 11L
    private val now = Instant.parse("2026-08-18T00:00:00Z")

    private fun confirmedKey(memberId: Long = authorId) = requireNotNull(PhotoObjectKey.parse("images/community/$memberId/${UUID.randomUUID()}"))

    private fun create(
        body: String = "웨이팅 있나요?",
        photoKeys: List<PhotoObjectKey> = emptyList(),
    ) = SpotQuestion.create(
        catalogId = catalogId,
        authorId = authorId,
        body = body,
        photoKeys = photoKeys,
    )

    @Test
    fun `본문 경계 - 1자와 500자는 허용되고 501자는 거부된다`() {
        assertEquals(1, create("가").body.length)
        assertEquals(500, create("가".repeat(500)).body.length)
        assertFailsWith<IllegalArgumentException> { create("가".repeat(501)) }
    }

    @Test
    fun `공백만 있는 본문은 거부되고 앞뒤 공백은 제거된다`() {
        assertFailsWith<IllegalArgumentException> { create("   ") }
        assertEquals("질문", create("  질문  ").body)
    }

    @Test
    fun `사진은 5장까지 허용되고 6장은 거부된다`() {
        val five = List(5) { confirmedKey() }
        assertEquals(5, create(photoKeys = five).photoKeys.size)
        assertFailsWith<IllegalArgumentException> { create(photoKeys = five + confirmedKey()) }
    }

    @Test
    fun `타인에게 발급된 사진 키는 거부된다`() {
        assertFailsWith<IllegalArgumentException> {
            create(photoKeys = listOf(confirmedKey(memberId = authorId + 1)))
        }
    }

    @Test
    fun `확정되지 않은 사진 키는 거부된다`() {
        val pending = PhotoObjectKey.issue(authorId, UUID.randomUUID())
        assertFailsWith<IllegalArgumentException> { create(photoKeys = listOf(pending)) }
    }

    @Test
    fun `새 질문은 미해결이고 답변 수가 0이다`() {
        val question = create()

        assertFalse(question.resolved)
        assertEquals(0, question.answerCount)
        assertFalse(question.hidden)
        assertFalse(question.deleted)
    }

    @Test
    fun `작성자는 해결 표시를 할 수 있고 멱등이다`() {
        val question = restored()

        assertTrue(question.changeResolvedBy(authorId, resolved = true))
        assertTrue(question.resolved)
        // 이미 해결됨에 다시 호출해도 성공이다(AC-8.4).
        assertTrue(question.changeResolvedBy(authorId, resolved = true))
        assertTrue(question.resolved)
    }

    /** R8 — 양방향이다. 되돌릴 수 없으면 잘못 누른 상태에 갇힌다. */
    @Test
    fun `작성자는 해결을 미해결로 되돌릴 수 있고 멱등이다`() {
        val question = restored()
        question.changeResolvedBy(authorId, resolved = true)

        assertTrue(question.changeResolvedBy(authorId, resolved = false))
        assertFalse(question.resolved)
        // 이미 미해결에 다시 호출해도 성공이다(AC-8.5).
        assertTrue(question.changeResolvedBy(authorId, resolved = false))
        assertFalse(question.resolved)
    }

    @Test
    fun `해결과 미해결을 오갈 수 있다`() {
        val question = restored()

        question.changeResolvedBy(authorId, resolved = true)
        assertTrue(question.resolved)
        question.changeResolvedBy(authorId, resolved = false)
        assertFalse(question.resolved)
        question.changeResolvedBy(authorId, resolved = true)
        assertTrue(question.resolved)
    }

    @Test
    fun `타인은 해결 상태를 바꿀 수 없고 상태가 그대로다`() {
        val question = restored()

        assertFalse(question.changeResolvedBy(authorId + 1, resolved = true))
        assertFalse(question.resolved)
    }

    /** 되돌리기도 소유자만 — 타인이 남의 질문을 미해결로 만들 수 없다. */
    @Test
    fun `타인은 해결된 질문을 되돌릴 수 없다`() {
        val question = restored()
        question.changeResolvedBy(authorId, resolved = true)

        assertFalse(question.changeResolvedBy(authorId + 1, resolved = false))
        assertTrue(question.resolved)
    }

    @Test
    fun `답변 수는 0 아래로 내려가지 않는다`() {
        val question = restored()

        question.decreaseAnswerCount()
        assertEquals(0, question.answerCount)

        question.increaseAnswerCount()
        question.increaseAnswerCount()
        question.decreaseAnswerCount()
        assertEquals(1, question.answerCount)
    }

    @Test
    fun `작성자만 삭제할 수 있다`() {
        val question = restored()

        assertFalse(question.deleteBy(authorId + 1, now))
        assertFalse(question.deleted)

        assertTrue(question.deleteBy(authorId, now))
        assertEquals(now, question.deletedAt)
    }

    @Test
    fun `숨겨진 질문은 작성자 본인에게만 보인다`() {
        val question = restored()
        question.hide()

        assertTrue(question.visibleTo(authorId))
        assertFalse(question.visibleTo(authorId + 1))
    }

    @Test
    fun `삭제된 질문은 작성자에게도 보이지 않는다`() {
        val question = restored()
        question.deleteBy(authorId, now)

        assertFalse(question.visibleTo(authorId))
    }

    private fun restored() = SpotQuestion.restore(
        id = 1L,
        catalogId = catalogId,
        authorId = authorId,
        body = "질문",
        photoKeys = emptyList(),
        resolved = false,
        answerCount = 0,
        hidden = false,
        deletedAt = null,
        createdAt = now,
    )
}

/** 답변·답글 불변식(SPEC-0026 R4, R7, R10). */
class SpotAnswerTest {

    private val authorId = 5L
    private val questionId = 2L
    private val answerId = 9L
    private val catalogId = 11L
    private val now = Instant.parse("2026-08-18T00:00:00Z")

    private fun confirmedKey(memberId: Long = authorId) = requireNotNull(PhotoObjectKey.parse("images/community/$memberId/${UUID.randomUUID()}"))

    private fun answer(body: String = "지금은 없어요", photoKeys: List<PhotoObjectKey> = emptyList()) = SpotAnswer.create(
        questionId = questionId,
        catalogId = catalogId,
        authorId = authorId,
        body = body,
        photoKeys = photoKeys,
    )

    private fun reply(body: String = "감사합니다", photoKeys: List<PhotoObjectKey> = emptyList()) = SpotAnswerReply.create(
        answerId = answerId,
        catalogId = catalogId,
        authorId = authorId,
        body = body,
        photoKeys = photoKeys,
    )

    @Test
    fun `답변 본문 경계 - 1자와 500자는 허용되고 501자는 거부된다`() {
        assertEquals(1, answer("가").body.length)
        assertEquals(500, answer("가".repeat(500)).body.length)
        assertFailsWith<IllegalArgumentException> { answer("가".repeat(501)) }
    }

    @Test
    fun `답글 본문 경계 - 1자와 500자는 허용되고 501자는 거부된다`() {
        assertEquals(1, reply("가").body.length)
        assertEquals(500, reply("가".repeat(500)).body.length)
        assertFailsWith<IllegalArgumentException> { reply("가".repeat(501)) }
    }

    @Test
    fun `공백만 있는 본문은 답변과 답글 모두에서 거부된다`() {
        assertFailsWith<IllegalArgumentException> { answer("  ") }
        assertFailsWith<IllegalArgumentException> { reply("  ") }
    }

    @Test
    fun `사진은 5장까지 허용되고 6장은 거부된다`() {
        val five = List(5) { confirmedKey() }
        assertEquals(5, answer(photoKeys = five).photoKeys.size)
        assertEquals(5, reply(photoKeys = five).photoKeys.size)
        assertFailsWith<IllegalArgumentException> { answer(photoKeys = five + confirmedKey()) }
        assertFailsWith<IllegalArgumentException> { reply(photoKeys = five + confirmedKey()) }
    }

    @Test
    fun `답변은 질문의 장소를 복사해 들고 있다`() {
        assertEquals(catalogId, answer().catalogId)
        assertEquals(questionId, answer().questionId)
    }

    @Test
    fun `작성자만 삭제할 수 있다`() {
        val target = SpotAnswer.restore(
            id = answerId, questionId = questionId, catalogId = catalogId, authorId = authorId,
            body = "답변", photoKeys = emptyList(), hidden = false, deletedAt = null, createdAt = now,
        )

        assertFalse(target.deleteBy(authorId + 1, now))
        assertTrue(target.deleteBy(authorId, now))
        assertEquals(now, target.deletedAt)
    }

    @Test
    fun `숨겨진 답변은 작성자 본인에게만 보인다`() {
        val target = SpotAnswer.restore(
            id = answerId, questionId = questionId, catalogId = catalogId, authorId = authorId,
            body = "답변", photoKeys = emptyList(), hidden = true, deletedAt = null, createdAt = now,
        )

        assertTrue(target.visibleTo(authorId))
        assertFalse(target.visibleTo(authorId + 1))
    }
}

/** DM 출처 스냅샷의 값 규칙(SPEC-0026 R14). */
class SpotQaOriginTest {

    private fun origin(
        questionExcerpt: String = "질문",
        answerExcerpt: String = "답변",
    ) = SpotQaOrigin(
        catalogId = 11L,
        spotTitle = "경복궁",
        questionId = 2L,
        questionExcerpt = questionExcerpt,
        answerId = 9L,
        answerExcerpt = answerExcerpt,
    )

    @Test
    fun `발췌는 100자로 잘린다`() {
        assertEquals(100, SpotQaOrigin.excerpt("가".repeat(501)).length)
    }

    @Test
    fun `발췌가 100자 이하면 그대로 둔다`() {
        assertEquals("짧은 질문", SpotQaOrigin.excerpt("짧은 질문"))
        assertEquals(100, SpotQaOrigin.excerpt("가".repeat(100)).length)
    }

    @Test
    fun `100자를 넘는 발췌는 거부된다`() {
        assertFailsWith<IllegalArgumentException> { origin(questionExcerpt = "가".repeat(101)) }
        assertFailsWith<IllegalArgumentException> { origin(answerExcerpt = "가".repeat(101)) }
    }

    @Test
    fun `각 필드가 제자리에 담긴다`() {
        val snapshot = origin()

        assertEquals(11L, snapshot.catalogId)
        assertEquals("경복궁", snapshot.spotTitle)
        assertEquals(2L, snapshot.questionId)
        assertEquals("질문", snapshot.questionExcerpt)
        assertEquals(9L, snapshot.answerId)
        assertEquals("답변", snapshot.answerExcerpt)
    }
}
