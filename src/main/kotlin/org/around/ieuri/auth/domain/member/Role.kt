package org.around.ieuri.auth.domain.member
import org.around.ieuri.auth.domain.member.Permission
/** 역할 애그리거트. 권한의 묶음이며, 회원↔역할 매핑은 조인 테이블(member_role)이 다룬다. */
class Role(
    val code: String,
    val name: String,
    val permissions: Set<Permission>,
) {
    /** 이 역할이 특정 권한을 부여하는가. */
    fun grants(permissionCode: String): Boolean = permissions.any { it.code == permissionCode }

    /** 역할 관리 능력(role:assign/role:revoke)을 주는 "관리 역할"인지. 자기 강등 방지 판정에 쓰인다. */
    fun isAdministrative(): Boolean = permissions.any { it.code in Permission.ROLE_MANAGEMENT }

    companion object {
        // 베이스라인 역할 코드. 시드(R__seed_rbac)와 일치한다.
        const val USER = "USER"
        const val ADMIN = "ADMIN"
    }
}
