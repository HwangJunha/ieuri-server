package org.around.ieuri.auth.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object MemberRoleTable : Table("auth.member_role") {
    val memberId = long("member_id").references(MemberTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_member_role_member")
    val roleId = long("role_id").references(RoleTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_member_role_role")
    val assignedAt = timestampWithTimeZone("assigned_at")
    override val primaryKey = PrimaryKey(memberId, roleId, name = "pk_member_role")

    init {
        index("idx_member_role_role", false, roleId)
    }
}
