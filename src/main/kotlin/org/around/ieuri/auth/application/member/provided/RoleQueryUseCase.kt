package org.around.ieuri.auth.application.member.provided
import org.around.ieuri.auth.application.member.provided.dto.RoleInfoView
/** 역할 조회 유스케이스. 인가는 리졸버가 판정한다(assignableRoles는 role:assign). */
interface RoleQueryUseCase {
    /** 부여 가능한 역할 목록. */
    fun assignableRoles(): List<RoleInfoView>
}
