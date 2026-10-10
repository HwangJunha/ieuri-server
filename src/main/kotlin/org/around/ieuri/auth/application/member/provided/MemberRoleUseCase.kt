package org.around.ieuri.auth.application.member.provided

import org.around.ieuri.auth.application.member.provided.dto.AssignRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.RevokeRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.RoleChangeResult

/** 역할 부여·회수(관리자). 둘 다 멱등이다. 인가는 리졸버가 판정한다(role:assign / role:revoke). */
interface MemberRoleUseCase {
    fun assign(command: AssignRoleCommand): RoleChangeResult

    fun revoke(command: RevokeRoleCommand): RoleChangeResult
}
