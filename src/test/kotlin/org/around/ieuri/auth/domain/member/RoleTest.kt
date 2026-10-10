package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.member.MemberFixtures.role
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 관리 역할 판정 — 역할 관리 권한(role:assign·role:revoke) 중 하나라도 주면 관리 역할이다. */
class RoleTest {

    @Test
    fun `역할 부여나 회수 권한 중 하나만 있어도 관리 역할이다`() {
        assertTrue(role(1L, "A", "role:assign").isAdministrative())
        assertTrue(role(2L, "B", "role:revoke").isAdministrative())
        assertTrue(role(3L, "C", "member:read", "role:assign").isAdministrative())
    }

    @Test
    fun `역할 관리 권한이 없으면 관리 역할이 아니다`() {
        assertFalse(role(1L, "A", "member:suspend", "member:reactivate", "member:read").isAdministrative())
        assertFalse(role(2L, "B").isAdministrative())
    }
}
