package org.around.ieuri.auth.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object EmailVerificationTable : Table("auth.email_verification") {
    val id = long("id").autoIncrement()
    val publicId = javaUUID("public_id")
    val memberId = long("member_id").references(MemberTable.id, ReferenceOption.NO_ACTION, ReferenceOption.NO_ACTION, "fk_ev_member")
    val codeHash = varchar("code_hash", 128)
    val expiresAt = timestampWithTimeZone("expires_at")
    val attempts = integer("attempts").default(0)
    val maxAttempts = integer("max_attempts").default(5)
    val consumed = bool("consumed").default(false)
    val verifiedAt = timestampWithTimeZone("verified_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val purpose = varchar("purpose", 20)
    val targetEmailEncrypted = varchar("target_email_encrypted", 512).nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_email_verification_public", publicId)
        index("idx_ev_member", false, memberId)
        // DB에서는 created_at이 DESC다.
        index("idx_ev_member_purpose", false, memberId, purpose, createdAt)
        check("ck_ev_purpose") { purpose inList listOf("REGISTRATION", "EMAIL_CHANGE") }
        check("ck_ev_target_email") { (purpose neq "EMAIL_CHANGE") or targetEmailEncrypted.isNotNull() }
    }
}
