package org.around.ieuri.community.infrastructure

import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.FeedItem
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.aggregate.MessageReport
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.ReportReason
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.FeedItemEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.FeedItemPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoEntities
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoKey
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 피드·DM 엔티티↔도메인 변환(SPEC-0017).
 *
 * **필드마다 다른 값을 준다** — 같은 값을 공유하면 두 필드가 뒤바뀌어도 통과한다.
 * 그것이 이 테스트가 존재하는 유일한 이유다.
 */
class FeedMapperTest {

    private val authorId = 11L
    private val spotId = 22L
    private val feedItemId = 33L
    private val createdAt = Instant.parse("2026-08-12T01:02:03Z")

    private fun photoKey(owner: Long = authorId): PhotoObjectKey =
        requireNotNull(PhotoObjectKey.parse("images/community/$owner/${UUID.randomUUID()}"))

    // ── FeedItem ────────────────────────────────────────────────────

    @Test
    fun `피드 엔티티가 도메인으로 변환된다`() {
        val keys = listOf(photoKey(), photoKey())
        val entity = FeedItemEntity(
            id = feedItemId,
            authorId = authorId,
            spotId = spotId,
            body = "본문",
            likeCount = 44,
            hidden = true,
            deletedAt = null,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        val domain = entity.toDomain(photoKeys = keys)

        assertEquals(feedItemId, domain.id)
        assertEquals(spotId, domain.spotId)
        assertEquals(authorId, domain.authorId)
        assertEquals("본문", domain.body)
        assertEquals(44, domain.likeCount)
        assertEquals(true, domain.hidden)
        assertNull(domain.deletedAt)
        assertEquals(createdAt, domain.createdAt)
        assertEquals(keys.map { it.value }, domain.photoKeys.map { it.value })
    }

    @Test
    fun `삭제된 피드의 deletedAt이 보존된다`() {
        val deletedAt = Instant.parse("2026-08-13T00:00:00Z")
        val entity = FeedItemEntity(
            id = feedItemId,
            authorId = authorId,
            spotId = spotId,
            body = "본문",
            likeCount = 0,
            hidden = false,
            deletedAt = deletedAt,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        assertEquals(deletedAt, entity.toDomain(photoKeys = listOf(photoKey())).deletedAt)
    }

    @Test
    fun `도메인 피드가 새 엔티티로 변환된다`() {
        val domain = FeedItem.create(
            spotId = spotId,
            authorId = authorId,
            body = "  본문  ",
            photoKeys = listOf(photoKey()),
        )

        val entity = domain.toNewEntity()

        assertEquals(authorId, entity.authorId)
        assertEquals(spotId, entity.spotId)
        assertEquals("본문", entity.body)
        assertEquals(0, entity.likeCount)
        assertEquals(false, entity.hidden)
        assertNull(entity.deletedAt)
        // id·시각은 DB가 채운다.
        assertNull(entity.id)
    }

    /** 배열 순서가 표시 순서이므로 position에 그대로 실려야 한다. */
    @Test
    fun `사진 엔티티의 position이 배열 순서를 따른다`() {
        val keys = List(3) { photoKey() }
        val domain = FeedItem.create(spotId, authorId, "본문", keys)

        val entities = domain.toPhotoEntities(feedItemId)

        assertEquals(3, entities.size)
        entities.forEachIndexed { index, entity ->
            assertEquals(feedItemId, entity.feedItemId)
            assertEquals(keys[index].value, entity.objectKey)
            assertEquals(index.toShort(), entity.position)
        }
    }

    @Test
    fun `사진 엔티티가 도메인 키로 변환된다`() {
        val key = photoKey()
        val entity = FeedItemPhotoEntity(
            id = 1L,
            feedItemId = feedItemId,
            objectKey = key.value,
            position = 0,
            createdAt = createdAt,
        )

        assertEquals(key.value, entity.toPhotoKey().value)
    }

    // ── Conversation ────────────────────────────────────────────────

    @Test
    fun `대화 엔티티가 도메인으로 변환된다`() {
        val lastMessageAt = Instant.parse("2026-08-12T05:06:07Z")
        val entity = ConversationEntity(
            id = 55L,
            memberLow = 66L,
            memberHigh = 77L,
            lastMessageAt = lastMessageAt,
            createdAt = createdAt,
        )

        val domain = entity.toDomain()

        assertEquals(55L, domain.id)
        assertEquals(66L, domain.memberLow)
        assertEquals(77L, domain.memberHigh)
        assertEquals(lastMessageAt, domain.lastMessageAt)
        assertEquals(createdAt, domain.createdAt)
    }

    @Test
    fun `도메인 대화가 새 엔티티로 변환된다`() {
        val now = Instant.parse("2026-08-12T09:00:00Z")
        val domain = Conversation.create(
            memberId = 99L,
            counterpartId = 66L,
            now = now,
        )

        val entity = domain.toNewEntity()

        // 정렬된 쌍이 그대로 실려야 유일 제약이 성립한다.
        assertEquals(66L, entity.memberLow)
        assertEquals(99L, entity.memberHigh)
        assertEquals(now, entity.lastMessageAt)
        assertNull(entity.id)
    }

    // ── Message ─────────────────────────────────────────────────────

    /** **복호화된 본문을 파라미터로 받는다** — 매퍼는 키를 알지 못하는 순수 함수다. */
    @Test
    fun `메시지 엔티티가 복호화된 본문과 함께 도메인으로 변환된다`() {
        val entity = MessageEntity(
            id = 111L,
            conversationId = 222L,
            senderId = 333L,
            bodyEncrypted = "ZW5jcnlwdGVk",
            createdAt = createdAt,
        )

        val domain = entity.toDomain(decryptedBody = "안녕하세요")

        assertEquals(111L, domain.id)
        assertEquals(222L, domain.conversationId)
        assertEquals(333L, domain.senderId)
        assertEquals("안녕하세요", domain.body)
        assertEquals(createdAt, domain.createdAt)
    }

    /** **평문이 컬럼에 들어가는 경로를 만들지 않는다** — 암호문을 파라미터로 받는다. */
    @Test
    fun `도메인 메시지가 암호문과 함께 새 엔티티로 변환된다`() {
        val domain = Message.create(conversationId = 222L, senderId = 333L, body = "안녕하세요")

        val entity = domain.toNewEntity(encryptedBody = "ZW5jcnlwdGVk")

        assertEquals(222L, entity.conversationId)
        assertEquals(333L, entity.senderId)
        assertEquals("ZW5jcnlwdGVk", entity.bodyEncrypted)
        assertNull(entity.feedItemId)
        assertNull(entity.photoObjectKey)
        assertNull(entity.id)
    }

    /** 첨부(피드 인용·사진)가 양방향으로 보존되어야 한다 — 필드마다 다른 값을 준다. */
    @Test
    fun `첨부가 있는 메시지가 양방향으로 변환된다`() {
        val key = photoKey(owner = 333L)
        val domain = Message.create(
            conversationId = 222L,
            senderId = 333L,
            body = "이 사진 보세요",
            feedItemId = 444L,
            photoKey = key,
        )

        val entity = domain.toNewEntity(encryptedBody = "ZW5j")

        assertEquals(444L, entity.feedItemId)
        assertEquals(key.value, entity.photoObjectKey)

        val restored = MessageEntity(
            id = 111L,
            conversationId = entity.conversationId,
            senderId = entity.senderId,
            bodyEncrypted = entity.bodyEncrypted,
            feedItemId = entity.feedItemId,
            photoObjectKey = entity.photoObjectKey,
            createdAt = createdAt,
        ).toDomain(decryptedBody = "이 사진 보세요")

        assertEquals(444L, restored.feedItemId)
        assertEquals(key.value, restored.photoKey?.value)
    }

    /** 첨부만 있는 메시지는 본문이 빈 문자열이며, 그 상태가 그대로 보존된다. */
    @Test
    fun `본문 없이 사진만 있는 메시지도 변환된다`() {
        val key = photoKey(owner = 333L)
        val domain = Message.create(222L, 333L, body = "", photoKey = key)

        val entity = domain.toNewEntity(encryptedBody = "")

        assertEquals("", entity.bodyEncrypted)
        assertEquals(key.value, entity.photoObjectKey)
        assertNull(entity.feedItemId)
    }

    // ── MessageReport ───────────────────────────────────────────────

    @Test
    fun `도메인 신고가 새 엔티티로 변환된다`() {
        val domain = MessageReport.create(
            reporterId = 444L,
            conversationId = 555L,
            messageIds = listOf(1L, 2L),
            reason = ReportReason.ABUSE,
            detail = "욕설",
        )

        val entity = domain.toNewEntity()

        assertEquals(444L, entity.reporterId)
        assertEquals(555L, entity.conversationId)
        assertEquals(ReportReason.ABUSE, entity.reason)
        assertEquals("욕설", entity.detail)
        assertNull(entity.id)
    }

    @Test
    fun `상세 사유가 없는 신고도 변환된다`() {
        val domain = MessageReport.create(444L, 555L, listOf(1L), ReportReason.SPAM, detail = null)

        assertNull(domain.toNewEntity().detail)
    }
}
