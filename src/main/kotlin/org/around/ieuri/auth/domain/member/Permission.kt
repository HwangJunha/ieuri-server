package org.around.ieuri.auth.domain.member
/** 권한 — 인가의 최소 단위(예: `member:suspend`). 인가는 역할명이 아니라 이 코드로 판정한다. */
@JvmInline
value class Permission(val code: String) {
    init {
        require(code.isNotBlank()) { "권한 코드는 비어 있을 수 없습니다." }
    }

    companion object {
        // 베이스라인 권한 코드. 시드(R__seed_rbac)와 일치해야 한다.
        const val MEMBER_SUSPEND = "member:suspend"
        const val MEMBER_REACTIVATE = "member:reactivate"
        const val ROLE_ASSIGN = "role:assign"
        const val ROLE_REVOKE = "role:revoke"
        const val MEMBER_READ = "member:read"

        /** 역할 관리 능력을 부여하는 권한. 이 권한을 주는 역할이 "관리 역할"이며 자기 강등 방지의 기준이 된다. */
        val ROLE_MANAGEMENT: Set<String> = setOf(ROLE_ASSIGN, ROLE_REVOKE)
    }
}
