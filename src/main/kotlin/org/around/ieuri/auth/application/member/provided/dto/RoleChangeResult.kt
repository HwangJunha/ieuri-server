package org.around.ieuri.auth.application.member.provided.dto
import org.around.ieuri.auth.application.member.provided.dto.AssignRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.RevokeRoleCommand
/** 역할 변경 결과. changed=false면 멱등(이미 부여됨/원래 없음)으로 실제 변경이 없었음을 뜻한다. */
data class RoleChangeResult(
    val success: Boolean,
    val changed: Boolean,
)
