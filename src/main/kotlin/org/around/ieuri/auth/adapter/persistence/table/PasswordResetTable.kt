package org.around.ieuri.auth.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object PasswordResetTable : Table("auth.password_reset") {
    val id = long("id").autoIncrement()
    val publicId = javaUUID("public_id")
    val memberId = long("member_id").references(MemberTable.id, ReferenceOption.NO_ACTION, ReferenceOption.NO_ACTION, "fk_pr_member")
    val codeHash = varchar("code_hash", 128)
    val expiresAt = timestampWithTimeZone("expires_at")
    val attempts = integer("attempts").default(0)
    val maxAttempts = integer("max_attempts").default(5)
    val consumed = bool("consumed").default(false)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_password_reset_public", publicId)
        index("idx_pr_member", false, memberId)
    }
}
