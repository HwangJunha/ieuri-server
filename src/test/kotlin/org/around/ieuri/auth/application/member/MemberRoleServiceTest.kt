package org.around.ieuri.auth.application.member

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.around.ieuri.auth.application.member.provided.dto.AssignRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.RevokeRoleCommand
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.provided.exception.RoleNotFoundException
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.MemberRoleChange
import org.around.ieuri.auth.domain.member.Permission
import org.around.ieuri.auth.domain.member.Role
import org.around.ieuri.auth.domain.member.SelfDemotionException
import org.around.ieuri.auth.domain.member.event.MemberRoleGranted
import org.around.ieuri.auth.domain.member.event.MemberRoleRevoked
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 역할 부여·회수. 둘 다 멱등이고, 회원 없음 → 역할 없음 → 자기 강등 순으로 답한다. */
class MemberRoleServiceTest {

    private val actorId = 1L
    private val targetId = 7L
    private val now = Instant.parse("2026-07-17T00:00:00Z")

    private val memberRepository = mockk<MemberRepository>(relaxUnitFun = true)
    private val eventPublisher = mockk<MemberEventPublisherPort>()
    private val events = mutableListOf<Any>()
    private val service = MemberRoleService(memberRepository, eventPublisher, Clock.fixed(now, ZoneOffset.UTC))

    private val adminRole = Role(id = 20, code = "ADMIN", name = "관리자", permissions = setOf(Permission(Permission.ROLE_ASSIGN), Permission(Permission.ROLE_REVOKE)))
    private val superAdminRole = Role(id = 30, code = "SUPER_ADMIN", name = "슈퍼관리자", permissions = setOf(Permission(Permission.ROLE_REVOKE)))
    private val userRole = Role(id = 10, code = "USER", name = "일반", permissions = emptySet())

    init {
        every { eventPublisher.publish(capture(events)) } just runs
        every { memberRepository.findById(targetId) } returns memberFixture(id = targetId)
        every { memberRepository.findById(actorId) } returns memberFixture(id = actorId)
        every { memberRepository.findRole("ADMIN") } returns adminRole
        every { memberRepository.findRole("USER") } returns userRole
        every { memberRepository.findRole("GHOST") } returns null
    }

    private fun givenHeld(memberId: Long, vararg roles: Role) {
        every { memberRepository.findRolesOf(memberId) } returns roles.toList()
    }

    private fun verifyNothingWritten() {
        verify(exactly = 0) { memberRepository.addRole(any(), any()) }
        verify(exactly = 0) { memberRepository.removeRole(any()) }
        assertTrue(events.isEmpty())
    }

    // --- 부여 ---

    @Test
    fun `갖지 않은 역할을 부여하면 매핑을 넣고 부여 사실을 알린다`() {
        givenHeld(targetId, userRole)

        val result = service.assign(AssignRoleCommand(actorMemberId = actorId, targetMemberId = targetId, roleCode = "ADMIN"))

        assertTrue(result.success)
        assertTrue(result.changed)
        verify(exactly = 1) { memberRepository.addRole(MemberRoleChange.Granted(targetId, adminRole), now) }
        assertEquals(listOf<Any>(MemberRoleGranted(memberId = targetId, roleCode = "ADMIN", actorMemberId = actorId)), events)
    }

    @Test
    fun `이미 가진 역할의 부여는 쓰지도 알리지도 않고 changed가 거짓이다`() {
        givenHeld(targetId, userRole, adminRole)

        val result = service.assign(AssignRoleCommand(actorId, targetId, "ADMIN"))

        assertTrue(result.success)
        assertFalse(result.changed)
        verifyNothingWritten()
    }

    @Test
    fun `부여는 회원 없음을 역할 없음보다 먼저 답한다`() {
        every { memberRepository.findById(targetId) } returns null

        val ex = assertFailsWith<MemberNotFoundException> { service.assign(AssignRoleCommand(actorId, targetId, "GHOST")) }

        assertEquals("회원을 찾을 수 없습니다.", ex.message)
        verify(exactly = 0) { memberRepository.findRole(any()) }
        verifyNothingWritten()
    }

    @Test
    fun `없는 역할의 부여는 역할 코드를 담아 거부한다`() {
        val ex = assertFailsWith<RoleNotFoundException> { service.assign(AssignRoleCommand(actorId, targetId, "GHOST")) }

        assertEquals("존재하지 않는 역할입니다: GHOST", ex.message)
        verifyNothingWritten()
    }

    // --- 회수 ---

    @Test
    fun `가진 역할을 회수하면 매핑을 지우고 회수 사실을 알린다`() {
        givenHeld(targetId, userRole, adminRole)

        val result = service.revoke(RevokeRoleCommand(actorMemberId = actorId, targetMemberId = targetId, roleCode = "ADMIN"))

        assertTrue(result.success)
        assertTrue(result.changed)
        verify(exactly = 1) { memberRepository.removeRole(MemberRoleChange.Revoked(targetId, adminRole)) }
        assertEquals(listOf<Any>(MemberRoleRevoked(memberId = targetId, roleCode = "ADMIN", actorMemberId = actorId)), events)
    }

    @Test
    fun `갖지 않은 역할의 회수는 쓰지도 알리지도 않고 changed가 거짓이다`() {
        givenHeld(targetId, userRole)

        val result = service.revoke(RevokeRoleCommand(actorId, targetId, "ADMIN"))

        assertTrue(result.success)
        assertFalse(result.changed)
        verifyNothingWritten()
    }

    @Test
    fun `회수는 회원 없음을 역할 없음보다 먼저 답한다`() {
        every { memberRepository.findById(targetId) } returns null

        val ex = assertFailsWith<MemberNotFoundException> { service.revoke(RevokeRoleCommand(actorId, targetId, "GHOST")) }

        assertEquals("회원을 찾을 수 없습니다.", ex.message)
        verify(exactly = 0) { memberRepository.findRole(any()) }
    }

    /** 본인의 마지막 관리 역할이어도, 역할 코드가 없으면 ROLE_NOT_FOUND가 먼저다. */
    @Test
    fun `회수는 역할 없음을 자기 강등보다 먼저 답한다`() {
        givenHeld(actorId, adminRole)

        val ex = assertFailsWith<RoleNotFoundException> { service.revoke(RevokeRoleCommand(actorId, actorId, "GHOST")) }

        assertEquals("존재하지 않는 역할입니다: GHOST", ex.message)
        verifyNothingWritten()
    }

    @Test
    fun `본인의 마지막 관리 역할은 스스로 회수할 수 없다`() {
        givenHeld(actorId, adminRole, userRole)

        val ex = assertFailsWith<SelfDemotionException> { service.revoke(RevokeRoleCommand(actorId, actorId, "ADMIN")) }

        assertEquals("마지막 관리 역할은 스스로 회수할 수 없습니다.", ex.message)
        verifyNothingWritten()
    }

    @Test
    fun `본인이라도 다른 관리 역할이 남으면 회수한다`() {
        givenHeld(actorId, adminRole, superAdminRole)

        val result = service.revoke(RevokeRoleCommand(actorId, actorId, "ADMIN"))

        assertTrue(result.changed)
        verify(exactly = 1) { memberRepository.removeRole(MemberRoleChange.Revoked(actorId, adminRole)) }
        assertEquals(listOf<Any>(MemberRoleRevoked(memberId = actorId, roleCode = "ADMIN", actorMemberId = actorId)), events)
    }

    @Test
    fun `본인의 비관리 역할 회수는 자기 강등에 걸리지 않는다`() {
        givenHeld(actorId, adminRole, userRole)

        val result = service.revoke(RevokeRoleCommand(actorId, actorId, "USER"))

        assertTrue(result.changed)
        verify(exactly = 1) { memberRepository.removeRole(MemberRoleChange.Revoked(actorId, userRole)) }
    }
}
