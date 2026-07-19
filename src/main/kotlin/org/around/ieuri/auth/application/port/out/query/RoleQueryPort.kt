package org.around.ieuri.auth.application.port.out.query

import org.around.ieuri.auth.application.query.RoleInfoView

/**
 * 역할 읽기 포트(CQRS 읽기 측). 애그리거트를 거치지 않고 read DTO를 직접 반환한다.
 */
interface RoleQueryPort {
    fun findAssignableRoles(): List<RoleInfoView>
}
