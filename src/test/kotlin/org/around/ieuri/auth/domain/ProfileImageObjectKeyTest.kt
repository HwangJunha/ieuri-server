package org.around.ieuri.auth.domain

import org.around.ieuri.auth.domain.member.ObjectKeyNotFoundException
import org.around.ieuri.auth.domain.member.ProfileImageObjectKey
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 프로필 이미지 오브젝트 키(SPEC-0011 R6). 커뮤니티와 네임스페이스가 갈리고, 소유권을 문자열만으로 판정한다. */
class ProfileImageObjectKeyTest {

    private val uuid = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val memberId = 7L

    @Test
    fun `발급 키는 pending profile memberId uuid 형태다`() {
        val key = ProfileImageObjectKey.issue(memberId, uuid)

        assertEquals("pending/profile/7/$uuid", key.value)
        assertEquals(memberId, key.memberId)
        assertFalse(key.confirmed)
    }

    @Test
    fun `확정 키는 접두어만 바뀐다`() {
        val confirmed = ProfileImageObjectKey.issue(memberId, uuid).toConfirmed()

        assertEquals("images/profile/7/$uuid", confirmed.value)
        assertTrue(confirmed.confirmed)
        assertEquals(memberId, confirmed.memberId)
    }

    @Test
    fun `이미 확정된 키를 다시 확정해도 그대로다`() {
        val once = ProfileImageObjectKey.issue(memberId, uuid).toConfirmed()
        assertEquals(once, once.toConfirmed())
    }

    @Test
    fun `발급한 키를 되읽을 수 있다`() {
        val key = ProfileImageObjectKey.issue(memberId, uuid)
        val parsed = ProfileImageObjectKey.parse(key.value)

        assertEquals(key, parsed)
    }

    @Test
    fun `커뮤니티 도메인 구간의 키는 거절한다`() {
        assertNull(ProfileImageObjectKey.parse("pending/community/7/$uuid"))
        assertNull(ProfileImageObjectKey.parse("images/community/7/$uuid"))
    }

    @Test
    fun `형식이 어긋나면 예외가 아니라 부재다`() {
        assertNull(ProfileImageObjectKey.parse("pending/profile/7"))
        assertNull(ProfileImageObjectKey.parse("pending/profile/7/$uuid/extra"))
        assertNull(ProfileImageObjectKey.parse("unknown/profile/7/$uuid"))
        assertNull(ProfileImageObjectKey.parse("pending/profile/abc/$uuid"))
        assertNull(ProfileImageObjectKey.parse("pending/profile/0/$uuid"))
        assertNull(ProfileImageObjectKey.parse("pending/profile/-1/$uuid"))
        assertNull(ProfileImageObjectKey.parse("pending/profile/7/not-a-uuid"))
        assertNull(ProfileImageObjectKey.parse(""))
    }

    @Test
    fun `소유권은 키의 memberId 구간으로 판정한다`() {
        val key = ProfileImageObjectKey.issue(memberId, uuid)

        assertTrue(key.ownedBy(memberId))
        assertFalse(key.ownedBy(99L))
    }

    @Test
    fun `자기에게 발급된 임시 키는 그대로 읽힌다`() {
        val key = ProfileImageObjectKey.pendingOf("pending/profile/7/$uuid", memberId)

        assertEquals("pending/profile/7/$uuid", key.value)
        assertEquals(memberId, key.memberId)
        assertFalse(key.confirmed)
    }

    @Test
    fun `남에게 발급된 임시 키는 없는 키로 답한다`() {
        val e = assertFailsWith<ObjectKeyNotFoundException> { ProfileImageObjectKey.pendingOf("pending/profile/8/$uuid", memberId) }

        assertEquals("발급된 적 없는 오브젝트 키입니다.", e.message)
        assertEquals("OBJECT_KEY_NOT_FOUND", e.code)
    }

    @Test
    fun `이미 확정된 키는 자기 것이어도 임시 키가 아니다`() {
        assertFailsWith<ObjectKeyNotFoundException> { ProfileImageObjectKey.pendingOf("images/profile/7/$uuid", memberId) }
    }

    @Test
    fun `형식이 어긋난 키는 소유권 실패와 같은 예외다`() {
        listOf("pending/community/7/$uuid", "pending/profile/7", "pending/profile/7/not-a-uuid", "").forEach { raw ->
            assertFailsWith<ObjectKeyNotFoundException> { ProfileImageObjectKey.pendingOf(raw, memberId) }
        }
    }

    @Test
    fun `memberId가 양수가 아니면 발급하지 않는다`() {
        listOf(0L, -1L).forEach {
            assertFailsWith<IllegalArgumentException> { ProfileImageObjectKey.issue(it, uuid) }
        }
    }
}
