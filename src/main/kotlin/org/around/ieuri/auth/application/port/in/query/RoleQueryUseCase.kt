package org.around.ieuri.auth.application.port.`in`.query

import org.around.ieuri.auth.application.query.RoleInfoView

/**
 * 역할 조회 유스케이스(CQRS 읽기 측). 인가는 인바운드 리졸버에서 판정한다(assignableRoles는 role:assign).
 */
interface RoleQueryUseCase {
    /** 부여 가능한 역할 목록. */
    fun assignableRoles(): List<RoleInfoView>
}
