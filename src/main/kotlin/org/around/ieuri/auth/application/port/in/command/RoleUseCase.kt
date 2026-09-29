package org.around.ieuri.auth.application.port.`in`.command

import org.around.ieuri.auth.application.command.AssignRoleCommand
import org.around.ieuri.auth.application.command.RevokeRoleCommand

/** 역할 부여/회수 유스케이스(관리자). 인가는 리졸버가 판정한다(role:assign / role:revoke). */
interface RoleUseCase {
    fun assign(command: AssignRoleCommand): RoleChangeResult

    fun revoke(command: RevokeRoleCommand): RoleChangeResult
}

/** 역할 변경 결과. changed=false면 멱등(이미 부여됨/원래 없음)으로 실제 변경이 없었음을 뜻한다. */
data class RoleChangeResult(
    val success: Boolean,
    val changed: Boolean,
)
