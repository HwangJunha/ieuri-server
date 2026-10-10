package org.around.ieuri.auth.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object MemberTable : Table("auth.member") {
    val id = long("id").autoIncrement()
    val provider = varchar("provider", 20)
    val providerUserId = varchar("provider_user_id", 255)
    val emailEncrypted = varchar("email_encrypted", 512)

    /** 정규화 이메일의 HMAC. [emailEncrypted]와 항상 함께 갱신한다. */
    val emailIdentifier = varchar("email_identifier", 64)
    val emailVerified = bool("email_verified").default(false)
    val passwordHash = varchar("password_hash", 255).nullable()
    val providerRefreshTokenEncrypted = varchar("provider_refresh_token_encrypted", 1024).nullable()
    val status = varchar("status", 20).default("ACTIVE")
    val ageVerified = bool("age_verified").default(false)
    val suspendedAt = timestampWithTimeZone("suspended_at").nullable()
    val suspendedUntil = timestampWithTimeZone("suspended_until").nullable()
    val suspensionReason = varchar("suspension_reason", 500).nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_member_provider_user", provider, providerUserId)
        uniqueIndex("uq_member_provider_email", provider, emailIdentifier)
        check("ck_member_email_password") { (provider neq "EMAIL") or passwordHash.isNotNull() }
        check("ck_member_provider") { provider inList listOf("APPLE", "GOOGLE", "KAKAO", "EMAIL") }
        check("ck_member_status") { status inList listOf("ACTIVE", "SUSPENDED") }
    }
}
