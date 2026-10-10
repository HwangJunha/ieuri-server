package org.around.ieuri.auth.domain

import org.around.ieuri.auth.domain.member.Email
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.Nickname
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderUserId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Member 상태 전이(정지/복구) 불변식 — 순수 도메인 단위테스트. */
class MemberLifecycleTest {

    private val now: Instant = Instant.parse("2026-07-17T00:00:00Z")

    private fun activeMember(): Member = Member.register(
        provider = Provider.GOOGLE,
        providerUserId = ProviderUserId("google-sub-1"),
        providerEmail = Email("u@example.com"),
        fallbackEmail = Email("u@example.com"),
        nickname = Nickname("여행자"),
        profileImageUrl = null,
        ageVerified = true,
        agreements = emptyList(),
        requiredTermsCodes = emptySet(),
        now = now,
    )

    @Test
    fun `정지하면 SUSPENDED로 전이되고 사유·기간이 기록된다`() {
        val member = activeMember()
        val until = now.plusSeconds(3600)
        member.suspend("약관 위반", until, now)

        assertEquals(MemberStatus.SUSPENDED, member.status)
        assertEquals("약관 위반", member.suspensionReason)
        assertEquals(until, member.suspendedUntil)
        assertEquals(now, member.suspendedAt)
    }

    @Test
    fun `정지 사유가 비어 있으면 거부된다`() {
        assertFailsWith<IllegalArgumentException> { activeMember().suspend(" ", null, now) }
    }

    @Test
    fun `정지 만료일이 과거면 거부된다`() {
        assertFailsWith<IllegalArgumentException> { activeMember().suspend("사유", now.minusSeconds(1), now) }
    }

    @Test
    fun `복구는 SUSPENDED에서 ACTIVE로 전이하고 정지 메타를 지운다`() {
        val member = activeMember()
        member.suspend("사유", now.plusSeconds(3600), now)
        member.reactivate(now.plusSeconds(10))

        assertEquals(MemberStatus.ACTIVE, member.status)
        assertNull(member.suspendedUntil)
        assertNull(member.suspensionReason)
        assertNull(member.suspendedAt)
    }

    @Test
    fun `ACTIVE 상태에서 복구를 시도하면 거부된다`() {
        assertFailsWith<IllegalArgumentException> { activeMember().reactivate(now) }
    }

    @Test
    fun `기간제 정지는 만료 시각 이후에 만료로 판정된다`() {
        val member = activeMember()
        member.suspend("사유", now.plusSeconds(60), now)

        assertFalse(member.isSuspensionExpired(now)) // 아직
        assertTrue(member.isSuspensionExpired(now.plusSeconds(61))) // 만료
    }

    @Test
    fun `무기한 정지는 만료되지 않는다`() {
        val member = activeMember()
        member.suspend("사유", null, now)
        assertFalse(member.isSuspensionExpired(now.plusSeconds(100_000)))
    }
}
