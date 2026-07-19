package org.around.ieuri.auth.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.application.command.AssignRoleCommand
import org.around.ieuri.auth.application.command.RevokeRoleCommand
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.exception.RoleNotFoundException
import org.around.ieuri.auth.application.exception.SelfDemotionException
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.RoleRepositoryPort
import org.around.ieuri.auth.domain.aggregate.Role
import org.around.ieuri.auth.domain.vo.Permission
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** RoleService — 부여/회수 오케스트레이션과 자기 강등 방지. */
class RoleServiceTest {

    private val memberRepository = mockk<MemberRepositoryPort>()
    private val roleRepository = mockk<RoleRepositoryPort>(relaxed = true)
    private val auditLog = mockk<AuditLogPort>(relaxed = true)

    private val roleService = RoleService(memberRepository, roleRepository, auditLog)

    private val adminRole = Role("ADMIN", "관리자", setOf(Permission(Permission.ROLE_ASSIGN), Permission(Permission.ROLE_REVOKE)))
    private val userRole = Role("USER", "일반", emptySet())

    // --- assign ---

    @Test
    fun `역할을 부여하면 매핑 추가·감사 기록`() {
        every { memberRepository.existsById(7L) } returns true
        every { roleRepository.findByCode("ADMIN") } returns adminRole
        every { roleRepository.assignRoleToMember(7L, "ADMIN") } returns true

        val result = roleService.assign(AssignRoleCommand(actorMemberId = 1L, targetMemberId = 7L, roleCode = "ADMIN"))

        assertTrue(result.changed)
        verify { auditLog.record(7L, "MEMBER_ROLE_ASSIGNED", any()) }
    }

    @Test
    fun `이미 부여된 역할은 멱등(changed=false)이고 감사 기록 없음`() {
        every { memberRepository.existsById(7L) } returns true
        every { roleRepository.findByCode("ADMIN") } returns adminRole
        every { roleRepository.assignRoleToMember(7L, "ADMIN") } returns false

        val result = roleService.assign(AssignRoleCommand(1L, 7L, "ADMIN"))

        assertFalse(result.changed)
        verify(exactly = 0) { auditLog.record(any(), "MEMBER_ROLE_ASSIGNED", any()) }
    }

    @Test
    fun `없는 회원에게 부여하면 예외`() {
        every { memberRepository.existsById(7L) } returns false
        assertFailsWith<MemberNotFoundException> { roleService.assign(AssignRoleCommand(1L, 7L, "ADMIN")) }
    }

    @Test
    fun `없는 역할을 부여하면 예외`() {
        every { memberRepository.existsById(7L) } returns true
        every { roleRepository.findByCode("GHOST") } returns null
        assertFailsWith<RoleNotFoundException> { roleService.assign(AssignRoleCommand(1L, 7L, "GHOST")) }
    }

    // --- revoke ---

    @Test
    fun `타인의 역할 회수는 정상 처리`() {
        every { memberRepository.existsById(7L) } returns true
        every { roleRepository.findByCode("ADMIN") } returns adminRole
        every { roleRepository.revokeRoleFromMember(7L, "ADMIN") } returns true

        val result = roleService.revoke(RevokeRoleCommand(actorMemberId = 1L, targetMemberId = 7L, roleCode = "ADMIN"))

        assertTrue(result.changed)
        verify { auditLog.record(7L, "MEMBER_ROLE_REVOKED", any()) }
    }

    @Test
    fun `본인의 마지막 관리 역할 회수는 거부된다(자기 강등 방지)`() {
        every { memberRepository.existsById(1L) } returns true
        every { roleRepository.findByCode("ADMIN") } returns adminRole
        // 대상(=본인)이 가진 역할은 ADMIN 하나뿐 → 다른 관리 역할 없음.
        every { roleRepository.findRolesOfMember(1L) } returns listOf(adminRole, userRole)

        assertFailsWith<SelfDemotionException> {
            roleService.revoke(RevokeRoleCommand(actorMemberId = 1L, targetMemberId = 1L, roleCode = "ADMIN"))
        }
        verify(exactly = 0) { roleRepository.revokeRoleFromMember(any(), any()) }
    }

    @Test
    fun `본인이라도 다른 관리 역할이 남으면 회수 허용`() {
        val superAdmin = Role("SUPER_ADMIN", "슈퍼관리자", setOf(Permission(Permission.ROLE_REVOKE)))
        every { memberRepository.existsById(1L) } returns true
        every { roleRepository.findByCode("ADMIN") } returns adminRole
        every { roleRepository.findRolesOfMember(1L) } returns listOf(adminRole, superAdmin)
        every { roleRepository.revokeRoleFromMember(1L, "ADMIN") } returns true

        val result = roleService.revoke(RevokeRoleCommand(1L, 1L, "ADMIN"))

        assertTrue(result.changed)
    }

    @Test
    fun `본인의 비관리 역할 회수는 자기 강등 방지에 걸리지 않는다`() {
        every { memberRepository.existsById(1L) } returns true
        every { roleRepository.findByCode("USER") } returns userRole
        every { roleRepository.revokeRoleFromMember(1L, "USER") } returns true

        val result = roleService.revoke(RevokeRoleCommand(1L, 1L, "USER"))

        assertTrue(result.changed)
        verify(exactly = 0) { roleRepository.findRolesOfMember(any()) }
    }
}
