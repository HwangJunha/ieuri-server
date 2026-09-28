package org.around.ieuri.community.infrastructure

import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toNewEntity
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** DM 엔티티↔도메인 변환(POL-033). 필드마다 다른 값을 준다. */
class ConversationMapperTest {

    private val createdAt = Instant.parse("2026-08-10T01:02:03Z")
    private val lastMessageAt = Instant.parse("2026-08-11T04:05:06Z")

    private fun confirmedKey(owner: Long) = requireNotNull(
        PhotoObjectKey.parse("images/community/$owner/${UUID.randomUUID()}"),
    )

    @Test
    fun `대화 엔티티가 도메인으로 옮겨진다`() {
        val entity = ConversationEntity(
            id = 11L,
            memberLow = 22L,
            memberHigh = 33L,
            lastMessageAt = lastMessageAt,
            createdAt = createdAt,
            lastReadMessageIdLow = 44L,
            lastReadMessageIdHigh = 55L,
        )

        val conversation = entity.toDomain()

        assertEquals(11L, conversation.id)
        assertEquals(22L, conversation.memberLow)
        assertEquals(33L, conversation.memberHigh)
        assertEquals(lastMessageAt, conversation.lastMessageAt)
        assertEquals(44L, conversation.lastReadMessageIdLow)
        assertEquals(55L, conversation.lastReadMessageIdHigh)
    }

    /** 아직 아무것도 읽지 않은 대화 — 읽음 지점은 0이 아니라 null이다. */
    @Test
    fun `읽음 지점이 없는 대화도 복원된다`() {
        val entity = ConversationEntity(
            id = 12L,
            memberLow = 23L,
            memberHigh = 34L,
            lastMessageAt = lastMessageAt,
            createdAt = createdAt,
            lastReadMessageIdLow = null,
            lastReadMessageIdHigh = null,
        )

        val conversation = entity.toDomain()

        assertNull(conversation.lastReadMessageIdLow)
        assertNull(conversation.lastReadMessageIdHigh)
    }

    @Test
    fun `대화 도메인이 새 엔티티로 옮겨진다`() {
        val conversation = Conversation.restore(
            id = 13L,
            memberLow = 24L,
            memberHigh = 35L,
            lastMessageAt = lastMessageAt,
            createdAt = createdAt,
            lastReadMessageIdLow = 46L,
            lastReadMessageIdHigh = 57L,
        )

        val entity = conversation.toNewEntity()

        assertEquals(24L, entity.memberLow)
        assertEquals(35L, entity.memberHigh)
        assertEquals(lastMessageAt, entity.lastMessageAt)
        assertEquals(46L, entity.lastReadMessageIdLow)
        assertEquals(57L, entity.lastReadMessageIdHigh)
        // id·createdAt은 DB가 채운다 — 새 엔티티에 실어 보내지 않는다.
        assertNull(entity.id)
    }

    @Test
    fun `메시지 엔티티가 도메인으로 옮겨진다`() {
        val key = confirmedKey(owner = 61L)
        val entity = MessageEntity(
            id = 71L,
            conversationId = 72L,
            senderId = 61L,
            bodyEncrypted = "암호문",
            photoObjectKey = key.value,
            createdAt = createdAt,
        )

        val message = entity.toDomain(decryptedBody = "평문 본문")

        assertEquals(71L, message.id)
        assertEquals(72L, message.conversationId)
        assertEquals(61L, message.senderId)
        // **복호화한 평문이 실린다** — 암호문이 도메인으로 새지 않는다(R16).
        assertEquals("평문 본문", message.body)
        assertEquals(key.value, message.photoKey?.value)
        assertEquals(createdAt, message.createdAt)
    }

    /** SPEC-0021 AC4.2 — 본문도 사진도 없는 과거 메시지가 조회 경로에서 살아남는다. */
    @Test
    fun `본문과 사진이 모두 빈 과거 메시지도 도메인으로 옮겨진다`() {
        val entity = MessageEntity(
            id = 73L,
            conversationId = 74L,
            senderId = 62L,
            bodyEncrypted = "",
            photoObjectKey = null,
            createdAt = createdAt,
        )

        val message = entity.toDomain(decryptedBody = "")

        assertEquals(73L, message.id)
        assertEquals(74L, message.conversationId)
        assertEquals(62L, message.senderId)
        assertEquals("", message.body)
        assertFalse(message.hasBody)
        assertNull(message.photoKey)
    }

    /** **[encryptedBody]를 받는다** — 평문이 컬럼에 들어가는 경로를 만들지 않는다(R16). */
    @Test
    fun `메시지 도메인이 새 엔티티로 옮겨진다`() {
        val key = confirmedKey(owner = 63L)
        val message = Message.create(
            conversationId = 75L,
            senderId = 63L,
            body = "보낼 본문",
            photoKey = key,
        )

        val entity = message.toNewEntity(encryptedBody = "암호문2")

        assertEquals(75L, entity.conversationId)
        assertEquals(63L, entity.senderId)
        assertEquals("암호문2", entity.bodyEncrypted)
        assertEquals(key.value, entity.photoObjectKey)
        assertNull(entity.id)
    }

    @Test
    fun `사진 없는 메시지는 키가 null로 옮겨진다`() {
        val message = Message.create(conversationId = 76L, senderId = 64L, body = "본문만")

        val entity = message.toNewEntity(encryptedBody = "암호문3")

        assertEquals(76L, entity.conversationId)
        assertEquals(64L, entity.senderId)
        assertNull(entity.photoObjectKey)
    }
}
