package org.around.ieuri.auth.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object RolePermissionTable : Table("auth.role_permission") {
    val roleId = long("role_id").references(RoleTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_role_permission_role")
    val permissionId = long("permission_id").references(PermissionTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_role_permission_permission")
    override val primaryKey = PrimaryKey(roleId, permissionId, name = "pk_role_permission")
}
