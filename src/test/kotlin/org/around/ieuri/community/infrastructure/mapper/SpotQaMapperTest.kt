package org.around.ieuri.community.infrastructure.mapper

import org.around.ieuri.community.domain.aggregate.SpotAnswer
import org.around.ieuri.community.domain.aggregate.SpotAnswerReply
import org.around.ieuri.community.domain.aggregate.SpotQuestion
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotAnswerEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotAnswerReplyEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQaPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQaPhotoOwnerType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQuestionEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoEntities
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoKey
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 장소 질문/답변의 엔티티↔도메인 변환(SPEC-0026).
 *
 * **필드마다 다른 값을 쓴다** — 같은 값을 공유하면 두 필드가 뒤바뀌어도 통과한다.
 * id 계열(catalogId·questionId·answerId·authorId)이 전부 Long이라 특히 그렇다.
 */
class SpotQaMapperTest {

    private val createdAt = Instant.parse("2026-08-18T01:02:03.123456789Z")
    private val deletedAt = Instant.parse("2026-08-19T04:05:06Z")

    private fun key(memberId: Long, uuid: UUID = UUID.randomUUID()) =
        requireNotNull(PhotoObjectKey.parse("images/community/$memberId/$uuid"))

    // ── 질문 ──────────────────────────────────────────────────────

    @Test
    fun `질문 엔티티가 도메인으로 필드별로 옮겨진다`() {
        val photoKey = key(memberId = 31L)
        val entity = SpotQuestionEntity(
            id = 11L,
            catalogId = 22L,
            authorId = 31L,
            body = "본문",
            resolved = true,
            answerCount = 4,
            hidden = true,
            deletedAt = deletedAt,
            createdAt = createdAt,
        )

        val question = entity.toDomain(photoKeys = listOf(photoKey))

        assertEquals(11L, question.id)
        assertEquals(22L, question.catalogId)
        assertEquals(31L, question.authorId)
        assertEquals("본문", question.body)
        assertTrue(question.resolved)
        assertEquals(4, question.answerCount)
        assertTrue(question.hidden)
        assertEquals(deletedAt, question.deletedAt)
        assertEquals(createdAt, question.createdAt)
        assertEquals(listOf(photoKey.value), question.photoKeys.map { it.value })
    }

    @Test
    fun `삭제되지 않은 질문은 deletedAt이 null이고 사진이 비어도 변환된다`() {
        val entity = SpotQuestionEntity(
            id = 11L, catalogId = 22L, authorId = 31L, body = "본문",
            resolved = false, answerCount = 0, hidden = false,
            deletedAt = null, createdAt = createdAt,
        )

        val question = entity.toDomain(photoKeys = emptyList())

        assertNull(question.deletedAt)
        assertTrue(question.photoKeys.isEmpty())
    }

    @Test
    fun `도메인 질문이 새 엔티티로 필드별로 옮겨진다`() {
        val question = SpotQuestion.restore(
            id = 11L, catalogId = 22L, authorId = 31L, body = "본문",
            photoKeys = emptyList(), resolved = true, answerCount = 4,
            hidden = true, deletedAt = deletedAt, createdAt = createdAt,
        )

        val entity = question.toNewEntity()

        // id·시각은 DB가 채우므로 새 엔티티에 실리지 않는다.
        assertNull(entity.id)
        assertNull(entity.createdAt)
        assertEquals(22L, entity.catalogId)
        assertEquals(31L, entity.authorId)
        assertEquals("본문", entity.body)
        assertTrue(entity.resolved)
        assertEquals(4, entity.answerCount)
        assertTrue(entity.hidden)
        assertEquals(deletedAt, entity.deletedAt)
    }

    // ── 답변 ──────────────────────────────────────────────────────

    @Test
    fun `답변 엔티티가 도메인으로 필드별로 옮겨진다`() {
        val entity = SpotAnswerEntity(
            id = 41L,
            questionId = 52L,
            catalogId = 63L,
            authorId = 74L,
            body = "답변 본문",
            hidden = true,
            deletedAt = deletedAt,
            createdAt = createdAt,
        )

        val answer = entity.toDomain(photoKeys = emptyList())

        assertEquals(41L, answer.id)
        assertEquals(52L, answer.questionId)
        assertEquals(63L, answer.catalogId)
        assertEquals(74L, answer.authorId)
        assertEquals("답변 본문", answer.body)
        assertTrue(answer.hidden)
        assertEquals(deletedAt, answer.deletedAt)
        assertEquals(createdAt, answer.createdAt)
    }

    @Test
    fun `도메인 답변이 새 엔티티로 필드별로 옮겨진다`() {
        val answer = SpotAnswer.restore(
            id = 41L, questionId = 52L, catalogId = 63L, authorId = 74L, body = "답변 본문",
            photoKeys = emptyList(), hidden = true, deletedAt = deletedAt, createdAt = createdAt,
        )

        val entity = answer.toNewEntity()

        assertNull(entity.id)
        assertEquals(52L, entity.questionId)
        assertEquals(63L, entity.catalogId)
        assertEquals(74L, entity.authorId)
        assertEquals("답변 본문", entity.body)
        assertTrue(entity.hidden)
        assertEquals(deletedAt, entity.deletedAt)
    }

    // ── 답글 ──────────────────────────────────────────────────────

    @Test
    fun `답글 엔티티가 도메인으로 필드별로 옮겨진다`() {
        val entity = SpotAnswerReplyEntity(
            id = 81L,
            answerId = 92L,
            catalogId = 103L,
            authorId = 114L,
            body = "답글 본문",
            hidden = true,
            deletedAt = deletedAt,
            createdAt = createdAt,
        )

        val reply = entity.toDomain(photoKeys = emptyList())

        assertEquals(81L, reply.id)
        assertEquals(92L, reply.answerId)
        assertEquals(103L, reply.catalogId)
        assertEquals(114L, reply.authorId)
        assertEquals("답글 본문", reply.body)
        assertTrue(reply.hidden)
        assertEquals(deletedAt, reply.deletedAt)
        assertEquals(createdAt, reply.createdAt)
    }

    @Test
    fun `도메인 답글이 새 엔티티로 필드별로 옮겨진다`() {
        val reply = SpotAnswerReply.restore(
            id = 81L, answerId = 92L, catalogId = 103L, authorId = 114L, body = "답글 본문",
            photoKeys = emptyList(), hidden = false, deletedAt = null, createdAt = createdAt,
        )

        val entity = reply.toNewEntity()

        assertNull(entity.id)
        assertEquals(92L, entity.answerId)
        assertEquals(103L, entity.catalogId)
        assertEquals(114L, entity.authorId)
        assertEquals("답글 본문", entity.body)
        assertEquals(false, entity.hidden)
        assertNull(entity.deletedAt)
    }

    // ── 사진 ──────────────────────────────────────────────────────

    @Test
    fun `사진 키 배열의 순서가 position이 된다`() {
        val keys = listOf(key(memberId = 5L), key(memberId = 5L), key(memberId = 5L))

        val entities = keys.toPhotoEntities(SpotQaPhotoOwnerType.ANSWER, ownerId = 77L)

        assertEquals(listOf<Short>(0, 1, 2), entities.map { it.position })
        assertEquals(keys.map { it.value }, entities.map { it.objectKey })
        assertTrue(entities.all { it.ownerType == SpotQaPhotoOwnerType.ANSWER })
        assertTrue(entities.all { it.ownerId == 77L })
    }

    @Test
    fun `사진이 없으면 빈 목록이 된다`() {
        assertTrue(emptyList<PhotoObjectKey>().toPhotoEntities(SpotQaPhotoOwnerType.QUESTION, 1L).isEmpty())
    }

    @Test
    fun `사진 엔티티가 키로 옮겨진다`() {
        val raw = "images/community/5/${UUID.randomUUID()}"
        val entity = SpotQaPhotoEntity(
            ownerType = SpotQaPhotoOwnerType.REPLY, ownerId = 3L, objectKey = raw, position = 0,
        )

        val photoKey = entity.toPhotoKey()

        assertEquals(raw, photoKey.value)
        assertEquals(5L, photoKey.memberId)
        assertTrue(photoKey.confirmed)
    }

    /** 적재된 키가 파싱되지 않으면 데이터 손상이다 — 조용히 버리면 사진이 이유 없이 사라진다. */
    @Test
    fun `형식이 깨진 사진 키는 실패시킨다`() {
        val entity = SpotQaPhotoEntity(
            ownerType = SpotQaPhotoOwnerType.QUESTION, ownerId = 3L,
            objectKey = "깨진키", position = 0,
        )

        assertFailsWith<IllegalArgumentException> { entity.toPhotoKey() }
    }
}
