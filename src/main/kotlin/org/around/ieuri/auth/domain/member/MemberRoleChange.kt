package org.around.ieuri.auth.domain.member

/** 역할 부여·회수의 결정. 이미 그 상태면 [Unchanged]다(멱등). */
sealed interface MemberRoleChange {
    data class Granted(val memberId: Long, val role: Role) : MemberRoleChange

    data class Revoked(val memberId: Long, val role: Role) : MemberRoleChange

    data object Unchanged : MemberRoleChange
}
