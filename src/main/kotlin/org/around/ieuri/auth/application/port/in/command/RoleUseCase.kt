package org.around.ieuri.auth.application.port.`in`.command

import org.around.ieuri.auth.application.command.AssignRoleCommand
import org.around.ieuri.auth.application.command.RevokeRoleCommand

/**
 * 역할 변경 유스케이스(관리자). 부여/회수를 하나로 묶는다.
 * 인가는 인바운드 리졸버에서 판정한다: 부여는 role:assign, 회수는 role:revoke 권한.
 */
interface RoleUseCase {
    fun assign(command: AssignRoleCommand): RoleChangeResult

    fun revoke(command: RevokeRoleCommand): RoleChangeResult
}

/** 역할 변경 결과. changed=false면 멱등(이미 부여됨/원래 없음)으로 실제 변경이 없었음을 뜻한다. */
data class RoleChangeResult(
    val success: Boolean,
    val changed: Boolean,
)
