package org.around.ieuri.community.domain.shared

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 오브젝트 키의 소유권 판정(R24). 저장소를 묻지 않고 문자열만으로 답한다. */
class PhotoObjectKeyTest {

    private val memberId = 11L

    @Test
    fun `발급된 키는 pending 접두어와 memberId를 담는다`() {
        val uuid = UUID.fromString("11111111-2222-3333-4444-555555555555")
        val key = PhotoObjectKey.issue(memberId, uuid)
        assertEquals("pending/community/11/$uuid", key.value)
        assertEquals(memberId, key.memberId)
        assertFalse(key.confirmed)
    }

    @Test
    fun `연속 발급된 두 키는 서로 다르다`() {
        val first = PhotoObjectKey.issue(memberId, UUID.randomUUID())
        val second = PhotoObjectKey.issue(memberId, UUID.randomUUID())
        assertNotEquals(first.value, second.value)
    }

    @Test
    fun `발급받은 키는 본인 소유로 판정된다`() {
        val key = PhotoObjectKey.issue(memberId, UUID.randomUUID())
        assertTrue(key.ownedBy(memberId))
        assertFalse(key.ownedBy(memberId + 1))
    }

    @Test
    fun `확정 전환은 접두어만 바꾸고 uuid는 보존한다`() {
        val uuid = UUID.randomUUID()
        val confirmed = PhotoObjectKey.issue(memberId, uuid).toConfirmed()
        assertEquals("images/community/$memberId/$uuid", confirmed.value)
        assertTrue(confirmed.confirmed)
        assertEquals(memberId, confirmed.memberId)
    }

    @Test
    fun `이미 확정된 키를 다시 전환해도 그대로다`() {
        val confirmed = PhotoObjectKey.issue(memberId, UUID.randomUUID()).toConfirmed()
        assertEquals(confirmed, confirmed.toConfirmed())
    }

    @Test
    fun `발급 절차를 거치지 않은 임의 문자열은 파싱되지 않는다`() {
        assertNull(PhotoObjectKey.parse("아무거나"))
        assertNull(PhotoObjectKey.parse("pending/community/11"))
        assertNull(PhotoObjectKey.parse("pending/community/11/not-a-uuid"))
        // 알 수 없는 접두어.
        assertNull(PhotoObjectKey.parse("secret/community/11/${UUID.randomUUID()}"))
        // memberId 구간이 숫자가 아니거나 0 이하.
        assertNull(PhotoObjectKey.parse("pending/community/abc/${UUID.randomUUID()}"))
        assertNull(PhotoObjectKey.parse("pending/community/0/${UUID.randomUUID()}"))
    }

    @Test
    fun `파싱된 키는 원본 문자열과 소유자를 보존한다`() {
        val raw = "images/community/11/${UUID.randomUUID()}"
        val parsed = requireNotNull(PhotoObjectKey.parse(raw))
        assertEquals(raw, parsed.value)
        assertEquals(memberId, parsed.memberId)
        assertTrue(parsed.confirmed)
    }

    @Test
    fun `경로 조작으로 다른 회원의 키를 만들 수 없다`() {
        // 구간이 4개가 아니면 거부되므로 상위 경로 탈출이 통하지 않는다.
        assertNull(PhotoObjectKey.parse("images/community/11/../12/${UUID.randomUUID()}"))
    }

    @Test
    fun `다른 도메인의 키는 거부된다`() {
        assertNull(PhotoObjectKey.parse("images/commerce/11/${UUID.randomUUID()}"))
        // 도메인 구간이 아예 빠진 옛 형식도 거부된다.
        assertNull(PhotoObjectKey.parse("images/11/${UUID.randomUUID()}"))
    }
}
