package org.around.ieuri.auth.application.member.required
import org.around.ieuri.auth.application.member.provided.dto.RoleInfoView
/** 역할 읽기 포트. read DTO를 직접 반환한다. */
interface RoleQueryPort {
    fun findAssignableRoles(): List<RoleInfoView>
}
