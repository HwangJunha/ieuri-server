package org.around.ieuri.auth.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object TermsTable : Table("auth.terms") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 50)
    val title = varchar("title", 200)
    val body = text("body")
    val version = varchar("version", 20)
    val required = bool("required")
    val effectiveAt = timestampWithTimeZone("effective_at")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_terms_code_version", code, version)
    }
}
