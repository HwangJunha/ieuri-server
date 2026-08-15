package org.around.ieuri.community.domain

import org.around.ieuri.community.domain.aggregate.FeedItem
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 피드 생성 불변식(SPEC-0017 R1·R2 — AC1-2, AC2-1~2-4). */
class FeedItemTest {

    private val authorId = 7L
    private val catalogId = 42L

    private fun confirmedKey(owner: Long = authorId): PhotoObjectKey =
        requireNotNull(PhotoObjectKey.parse("images/community/$owner/${UUID.randomUUID()}"))

    private fun pendingKey(owner: Long = authorId): PhotoObjectKey =
        requireNotNull(PhotoObjectKey.parse("pending/community/$owner/${UUID.randomUUID()}"))

    private fun create(
        catalogId: Long = this.catalogId,
        body: String = "오늘의 경복궁",
        photoKeys: List<PhotoObjectKey> = listOf(confirmedKey()),
    ) = FeedItem.create(catalogId = catalogId, authorId = authorId, body = body, photoKeys = photoKeys)

    @Test
    fun `사진 1장으로 생성된다 - 1장이 하한이다`() {
        val feedItem = create(photoKeys = listOf(confirmedKey()))

        assertEquals(catalogId, feedItem.catalogId)
        assertEquals(1, feedItem.photoKeys.size)
        assertEquals(0, feedItem.likeCount)
        assertFalse(feedItem.hidden)
    }

    /** AC2-2 — 배열 순서가 표시 순서이므로 보존되어야 한다. */
    @Test
    fun `사진 5장이 순서를 보존한 채 생성된다`() {
        val keys = List(5) { confirmedKey() }

        val feedItem = create(photoKeys = keys)

        assertEquals(keys.map { it.value }, feedItem.photoKeys.map { it.value })
    }

    /** AC2-3 — Post와 달리 사진 없는 피드는 존재할 수 없다. */
    @Test
    fun `사진이 0장이면 생성되지 않는다`() {
        assertFailsWith<IllegalArgumentException> { create(photoKeys = emptyList()) }
    }

    /** AC2-4 — **앞 5개만 취해 성공시키지 않는다.** 조용한 절삭은 올린 것을 말없이 버리는 것이다. */
    @Test
    fun `사진이 6장이면 앞 5장만 취하지 않고 거부된다`() {
        assertFailsWith<IllegalArgumentException> { create(photoKeys = List(6) { confirmedKey() }) }
    }

    /** AC1-2 — spot은 필수다. 양수가 아닌 id는 부재와 같이 다룬다. */
    @Test
    fun `catalogId가 유효하지 않으면 생성되지 않는다`() {
        assertFailsWith<IllegalArgumentException> { create(catalogId = 0L) }
    }

    @Test
    fun `본문이 공백만이면 생성되지 않는다`() {
        assertFailsWith<IllegalArgumentException> { create(body = "   ") }
    }

    @Test
    fun `본문은 앞뒤 공백이 제거되어 저장된다`() {
        assertEquals("경복궁", create(body = "  경복궁  ").body)
    }

    @Test
    fun `본문이 상한을 넘으면 생성되지 않는다`() {
        assertFailsWith<IllegalArgumentException> {
            create(body = "가".repeat(FeedItem.BODY_MAX_LENGTH + 1))
        }
    }

    @Test
    fun `확정되지 않은 사진 키로는 생성되지 않는다`() {
        assertFailsWith<IllegalArgumentException> { create(photoKeys = listOf(pendingKey())) }
    }

    @Test
    fun `타인에게 발급된 사진 키로는 생성되지 않는다`() {
        assertFailsWith<IllegalArgumentException> { create(photoKeys = listOf(confirmedKey(owner = 999L))) }
    }

    @Test
    fun `좋아요 수는 0 아래로 내려가지 않는다`() {
        val feedItem = create()

        feedItem.decreaseLikeCount()

        assertEquals(0, feedItem.likeCount)
    }

    @Test
    fun `좋아요 수가 오르고 내린다`() {
        val feedItem = create()

        feedItem.increaseLikeCount()
        feedItem.increaseLikeCount()
        feedItem.decreaseLikeCount()

        assertEquals(1, feedItem.likeCount)
    }

    /** 숨겨진 피드는 작성자 본인에게만 남는다. */
    @Test
    fun `숨겨진 피드는 작성자에게만 보인다`() {
        val feedItem = create().also { it.hide() }

        assertTrue(feedItem.visibleTo(authorId))
        assertFalse(feedItem.visibleTo(999L))
    }
}
