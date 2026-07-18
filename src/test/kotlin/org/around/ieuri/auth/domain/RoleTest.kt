package org.around.ieuri.auth.domain

import org.around.ieuri.auth.domain.aggregate.Role
import org.around.ieuri.auth.domain.vo.Permission
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Role 도메인 — 권한 판정과 "관리 역할" 판정(자기 강등 방지의 기준). */
class RoleTest {

    private fun role(vararg codes: String) =
        Role("ADMIN", "관리자", codes.map { Permission(it) }.toSet())

    @Test
    fun `grants는 보유 권한을 판정한다`() {
        val r = role(Permission.MEMBER_READ, Permission.MEMBER_SUSPEND)
        assertTrue(r.grants(Permission.MEMBER_READ))
        assertFalse(r.grants(Permission.ROLE_ASSIGN))
    }

    @Test
    fun `role 관리 권한을 주면 관리 역할이다`() {
        assertTrue(role(Permission.ROLE_ASSIGN).isAdministrative())
        assertTrue(role(Permission.ROLE_REVOKE).isAdministrative())
    }

    @Test
    fun `읽기·정지 권한만으로는 관리 역할이 아니다`() {
        // 자기 강등 방지는 role 관리 능력(assign/revoke) 보유 역할에만 적용된다.
        assertFalse(role(Permission.MEMBER_READ, Permission.MEMBER_SUSPEND).isAdministrative())
    }

    @Test
    fun `권한이 없는 역할(USER)은 관리 역할이 아니다`() {
        assertFalse(Role("USER", "일반", emptySet()).isAdministrative())
    }
}
