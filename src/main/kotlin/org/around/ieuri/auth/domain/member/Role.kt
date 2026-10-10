package org.around.ieuri.auth.domain.member

/** 역할 — 권한의 묶음. 시드로만 들어오며, 회원에게 주고 거두는 것은 [Member]가 정한다. */
data class Role(
    val id: Long,
    val code: String,
    val name: String,
    val permissions: Set<Permission>,
) {
    /** 역할 관리 능력(role:assign/role:revoke)을 주는 "관리 역할"인지. 자기 강등 방지의 기준이다. */
    fun isAdministrative(): Boolean = permissions.any { it.code in Permission.ROLE_MANAGEMENT }

    companion object {
        // 베이스라인 역할 코드. 시드와 일치한다.
        const val USER = "USER"
        const val ADMIN = "ADMIN"
    }
}
