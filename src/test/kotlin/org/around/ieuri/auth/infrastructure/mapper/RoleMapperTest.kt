package org.around.ieuri.auth.infrastructure.mapper

import org.around.ieuri.auth.domain.vo.Permission
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.RoleEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toDomain
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** RoleMapper 필드별 매핑 검증 — 순수 단위테스트(Spring/DB 없음). */
class RoleMapperTest {

    private fun entity(description: String? = "관리자 역할") = RoleEntity(
        id = 5L,
        code = "ADMIN",
        name = "관리자",
        description = description,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `역할 엔티티와 권한 코드를 도메인으로 옮긴다`() {
        val role = entity().toDomain(listOf(Permission.ROLE_ASSIGN, Permission.MEMBER_READ))

        assertEquals("ADMIN", role.code)
        assertEquals("관리자", role.name)
        assertEquals(
            setOf(Permission(Permission.ROLE_ASSIGN), Permission(Permission.MEMBER_READ)),
            role.permissions,
        )
        assertTrue(role.grants(Permission.ROLE_ASSIGN))
    }

    @Test
    fun `nullable 필드인 description이 null이어도 예외 없이 옮긴다`() {
        // description은 도메인 Role이 갖지 않는 필드이므로 매핑 대상이 아니다.
        val role = entity(description = null).toDomain(listOf(Permission.MEMBER_READ))

        assertEquals("ADMIN", role.code)
        assertEquals("관리자", role.name)
        assertEquals(1, role.permissions.size)
    }

    @Test
    fun `권한이 하나도 없으면 빈 권한 집합이 된다`() {
        val role = entity().toDomain(emptyList())

        assertTrue(role.permissions.isEmpty())
        assertEquals("ADMIN", role.code)
    }

    @Test
    fun `중복 권한 코드는 한 번만 담긴다`() {
        val role = entity().toDomain(listOf(Permission.MEMBER_READ, Permission.MEMBER_READ))

        assertEquals(1, role.permissions.size)
    }
}
