package org.around.ieuri.auth.domain.aggregate

import org.around.ieuri.auth.domain.vo.Permission

/**
 * 역할(Role) 애그리거트 — 권한(Permission)의 묶음이자 회원에게 부여되는 단위(예: ADMIN, USER).
 * 회원↔역할 매핑은 관계이므로 Member 애그리거트가 아니라 조인 테이블(member_role)로 다루고,
 * 역할 자체의 권한 구성만 이 애그리거트가 소유한다.
 */
class Role(
    val code: String,
    val name: String,
    val permissions: Set<Permission>,
) {
    /** 이 역할이 특정 권한을 부여하는가. */
    fun grants(permissionCode: String): Boolean = permissions.any { it.code == permissionCode }

    /**
     * 역할 관리 능력(role:assign/role:revoke)을 부여하는 "관리 역할"인지.
     * 자기 강등 방지: 본인의 마지막 관리 역할 회수를 거부하는 판정에 쓰인다.
     */
    fun isAdministrative(): Boolean = permissions.any { it.code in Permission.ROLE_MANAGEMENT }

    companion object {
        // 베이스라인 역할 코드. 시드(R__seed_rbac)와 일치한다.
        const val USER = "USER"
        const val ADMIN = "ADMIN"
    }
}
