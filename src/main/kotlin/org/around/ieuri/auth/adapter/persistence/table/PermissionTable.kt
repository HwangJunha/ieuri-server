package org.around.ieuri.auth.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object PermissionTable : Table("auth.permission") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 100)
    val description = varchar("description", 255).nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_permission_code", code)
    }
}
