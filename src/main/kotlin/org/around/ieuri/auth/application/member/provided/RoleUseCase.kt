package org.around.ieuri.auth.application.member.provided
import org.around.ieuri.auth.application.member.provided.dto.AssignRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.RevokeRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.RoleChangeResult
/** 역할 부여/회수 유스케이스(관리자). 인가는 리졸버가 판정한다(role:assign / role:revoke). */
interface RoleUseCase {
    fun assign(command: AssignRoleCommand): RoleChangeResult

    fun revoke(command: RevokeRoleCommand): RoleChangeResult
}
