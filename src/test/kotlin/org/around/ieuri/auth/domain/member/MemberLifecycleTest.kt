package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.member.MemberFixtures.NOW
import org.around.ieuri.auth.domain.member.MemberFixtures.member
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/** 정지·복구·로그인 허용 판정. 예외 메시지는 응답에 그대로 나간다. */
class MemberLifecycleTest {

    private val until = NOW.plusSeconds(86_400)

    private fun suspended(until: Instant?, reason: String? = "약관 위반") = member(
        status = MemberStatus.SUSPENDED,
        suspendedAt = NOW.minusSeconds(1000),
        suspendedUntil = until,
        suspensionReason = reason,
    )

    // ── 정지 ──

    @Test
    fun `정지하면 상태와 시각 사유 기간이 기록된다`() {
        val before = member()

        val after = before.suspend("약관 위반", until, NOW)

        assertEquals(MemberStatus.SUSPENDED, after.status)
        assertEquals(NOW, after.suspendedAt)
        assertEquals(until, after.suspendedUntil)
        assertEquals("약관 위반", after.suspensionReason)
        assertEquals(before, after.copy(status = MemberStatus.ACTIVE, suspendedAt = null, suspendedUntil = null, suspensionReason = null))
    }

    @Test
    fun `만료일 없이 정지하면 무기한이다`() {
        val after = member().suspend("스팸", until = null, NOW)

        assertEquals(MemberStatus.SUSPENDED, after.status)
        assertNull(after.suspendedUntil)
        assertEquals("스팸", after.suspensionReason)
    }

    @Test
    fun `이미 정지된 회원을 다시 정지하면 사유와 기간이 갱신된다`() {
        val later = NOW.plusSeconds(500)

        val after = suspended(until, reason = "이전 사유").suspend("새 사유", until = null, later)

        assertEquals(later, after.suspendedAt)
        assertNull(after.suspendedUntil)
        assertEquals("새 사유", after.suspensionReason)
    }

    @Test
    fun `정지 사유가 비면 거부한다`() {
        listOf("", "   ").forEach { blank ->
            val e = assertFailsWith<InvalidSuspensionException> { member().suspend(blank, until, NOW) }

            assertEquals("정지 사유는 필수입니다.", e.message)
            assertEquals("INVALID_INPUT", e.code)
        }
    }

    @Test
    fun `만료일은 현재보다 뒤여야 한다 - 정각도 거부한다`() {
        val atNow = assertFailsWith<InvalidSuspensionException> { member().suspend("사유", NOW, NOW) }
        val past = assertFailsWith<InvalidSuspensionException> { member().suspend("사유", NOW.minusMillis(1), NOW) }

        assertEquals("정지 만료일은 현재 이후여야 합니다.", atNow.message)
        assertEquals("정지 만료일은 현재 이후여야 합니다.", past.message)
        assertEquals(NOW.plusMillis(1), member().suspend("사유", NOW.plusMillis(1), NOW).suspendedUntil)
    }

    @Test
    fun `사유와 만료일이 모두 어긋나면 사유가 먼저 걸린다`() {
        val e = assertFailsWith<InvalidSuspensionException> { member().suspend(" ", NOW.minusSeconds(1), NOW) }

        assertEquals("정지 사유는 필수입니다.", e.message)
    }

    // ── 복구 ──

    @Test
    fun `복구하면 정지 정보가 모두 지워진다`() {
        val before = suspended(until)

        val after = before.reactivate()

        assertEquals(MemberStatus.ACTIVE, after.status)
        assertNull(after.suspendedAt)
        assertNull(after.suspendedUntil)
        assertNull(after.suspensionReason)
        assertEquals(before.email, after.email)
        assertEquals(before.id, after.id)
    }

    @Test
    fun `정지 상태가 아니면 복구할 수 없다`() {
        val e = assertFailsWith<NotSuspendedException> { member(status = MemberStatus.ACTIVE).reactivate() }

        assertEquals("정지 상태에서만 복구할 수 있습니다.", e.message)
        assertEquals("INVALID_INPUT", e.code)
    }

    // ── 로그인 허용 ──

    @Test
    fun `활성 회원은 그대로 통과한다`() {
        val member = member(status = MemberStatus.ACTIVE)

        assertSame(member, member.login(NOW))
    }

    @Test
    fun `유효한 정지는 사유를 붙여 거부한다`() {
        val e = assertFailsWith<InactiveMemberException> { suspended(until, reason = "약관 위반").login(until.minusMillis(1)) }

        assertEquals("정지된 계정입니다. 사유: 약관 위반", e.message)
        assertEquals("ACCOUNT_INACTIVE", e.code)
    }

    @Test
    fun `사유가 없는 정지는 사유 없이 거부한다`() {
        val e = assertFailsWith<InactiveMemberException> { suspended(until = null, reason = null).login(NOW) }

        assertEquals("정지된 계정입니다.", e.message)
    }

    @Test
    fun `기간제 정지는 만료 정각부터 복구된 회원으로 통과한다`() {
        val atUntil = suspended(until).login(until)
        val after = suspended(until).login(until.plusSeconds(1))

        assertEquals(MemberStatus.ACTIVE, atUntil.status)
        assertNull(atUntil.suspendedAt)
        assertNull(atUntil.suspendedUntil)
        assertNull(atUntil.suspensionReason)
        assertEquals(MemberStatus.ACTIVE, after.status)
    }

    @Test
    fun `무기한 정지는 아무리 지나도 만료되지 않는다`() {
        val e = assertFailsWith<InactiveMemberException> { suspended(until = null, reason = "영구 정지").login(NOW.plusSeconds(10L * 365 * 86_400)) }

        assertEquals("정지된 계정입니다. 사유: 영구 정지", e.message)
    }
}
