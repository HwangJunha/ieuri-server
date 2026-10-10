package org.around.ieuri.auth.adapter.persistence.query

import org.around.ieuri.auth.adapter.persistence.mapper.toRoleInfoView
import org.around.ieuri.auth.adapter.persistence.table.RoleTable
import org.around.ieuri.auth.application.member.provided.dto.RoleInfoView
import org.around.ieuri.auth.application.member.required.RoleQueryPort
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

/** 역할 읽기 포트 구현. 코드 순이다. */
@Component
class RoleQueryAdapter : RoleQueryPort {

    override fun findAssignableRoles(): List<RoleInfoView> = RoleTable.selectAll()
        .orderBy(RoleTable.code to SortOrder.ASC)
        .map { it.toRoleInfoView() }
}
