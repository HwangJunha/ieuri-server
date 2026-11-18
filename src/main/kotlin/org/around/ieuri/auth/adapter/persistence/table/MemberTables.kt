package org.around.ieuri.auth.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.java.javaUUID
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

object MemberProfileTable : Table("auth.member_profile") {
    val id = long("id").autoIncrement()
    val memberId = long("member_id").references(MemberTable.id, ReferenceOption.NO_ACTION, ReferenceOption.NO_ACTION, "fk_member_profile_member")
    val nickname = varchar("nickname", 50)
    val profileImageUrl = varchar("profile_image_url", 1024).nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_member_profile_member", memberId)
    }
}

object MemberTermsAgreementTable : Table("auth.member_terms_agreement") {
    val id = long("id").autoIncrement()
    val memberId = long("member_id").references(MemberTable.id, ReferenceOption.NO_ACTION, ReferenceOption.NO_ACTION, "fk_mta_member")
    val termsId = long("terms_id").references(TermsTable.id, ReferenceOption.NO_ACTION, ReferenceOption.NO_ACTION, "fk_mta_terms")
    val termsCode = varchar("terms_code", 50)
    val termsVersion = varchar("terms_version", 20)
    val agreed = bool("agreed")
    val agreedAt = timestampWithTimeZone("agreed_at")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_mta_member_terms", memberId, termsId)
        index("idx_mta_member", false, memberId)
    }
}

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

object MemberRoleTable : Table("auth.member_role") {
    val memberId = long("member_id").references(MemberTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_member_role_member")
    val roleId = long("role_id").references(RoleTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_member_role_role")
    val assignedAt = timestampWithTimeZone("assigned_at")
    override val primaryKey = PrimaryKey(memberId, roleId, name = "pk_member_role")

    init {
        index("idx_member_role_role", false, roleId)
    }
}

object RoleTable : Table("auth.role") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 50)
    val name = varchar("name", 100)
    val description = varchar("description", 255).nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_role_code", code)
    }
}

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

object RolePermissionTable : Table("auth.role_permission") {
    val roleId = long("role_id").references(RoleTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_role_permission_role")
    val permissionId = long("permission_id").references(PermissionTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_role_permission_permission")
    override val primaryKey = PrimaryKey(roleId, permissionId, name = "pk_role_permission")
}

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
