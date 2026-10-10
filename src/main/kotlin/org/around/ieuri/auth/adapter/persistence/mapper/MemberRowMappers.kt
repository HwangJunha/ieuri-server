package org.around.ieuri.auth.adapter.persistence.mapper

import org.around.ieuri.auth.adapter.persistence.table.EmailVerificationTable
import org.around.ieuri.auth.adapter.persistence.table.MemberProfileTable
import org.around.ieuri.auth.adapter.persistence.table.MemberTable
import org.around.ieuri.auth.adapter.persistence.table.MemberTermsAgreementTable
import org.around.ieuri.auth.adapter.persistence.table.PasswordResetTable
import org.around.ieuri.auth.adapter.persistence.table.RoleTable
import org.around.ieuri.auth.adapter.persistence.table.TermsTable
import org.around.ieuri.auth.domain.member.Agreement
import org.around.ieuri.auth.domain.member.Email
import org.around.ieuri.auth.domain.member.EmailVerification
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.PasswordHash
import org.around.ieuri.auth.domain.member.PasswordReset
import org.around.ieuri.auth.domain.member.Permission
import org.around.ieuri.auth.domain.member.Profile
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderUserId
import org.around.ieuri.auth.domain.member.Role
import org.around.ieuri.auth.domain.member.Terms
import org.around.ieuri.auth.domain.member.VerificationPurpose
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun Instant.toTimestamptz(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

/** 회원 행 → 도메인. 복호화한 이메일은 호출자가 넘긴다. */
fun ResultRow.toMember(decryptedEmail: String): Member = Member(
    id = this[MemberTable.id],
    provider = Provider.valueOf(this[MemberTable.provider]),
    providerUserId = ProviderUserId(this[MemberTable.providerUserId]),
    email = Email(decryptedEmail),
    emailVerified = this[MemberTable.emailVerified],
    status = MemberStatus.valueOf(this[MemberTable.status]),
    ageVerified = this[MemberTable.ageVerified],
    suspendedAt = this[MemberTable.suspendedAt]?.toInstant(),
    suspendedUntil = this[MemberTable.suspendedUntil]?.toInstant(),
    suspensionReason = this[MemberTable.suspensionReason],
    passwordHash = this[MemberTable.passwordHash]?.let { PasswordHash(it) },
)

/**
 * 새 회원 → 행. id와 시각은 DB가 채운다.
 * - 암호문과 [emailIdentifier](유니크 판정용 파생값)는 호출자가 계산해 넘긴다. 둘은 늘 함께 쓴다.
 */
fun Member.writeNewTo(row: UpdateBuilder<*>, encryptedEmail: String, emailIdentifier: String) {
    row[MemberTable.provider] = provider.name
    row[MemberTable.providerUserId] = providerUserId.value
    row[MemberTable.emailEncrypted] = encryptedEmail
    row[MemberTable.emailIdentifier] = emailIdentifier
    row[MemberTable.emailVerified] = emailVerified
    row[MemberTable.passwordHash] = passwordHash?.value
    row[MemberTable.status] = status.name
    row[MemberTable.ageVerified] = ageVerified
}

/** 이메일 교체 — 암호문·파생 식별자·로그인 식별자 세 열. */
fun Member.writeEmailTo(row: UpdateBuilder<*>, encryptedEmail: String, emailIdentifier: String) {
    row[MemberTable.emailEncrypted] = encryptedEmail
    row[MemberTable.emailIdentifier] = emailIdentifier
    row[MemberTable.providerUserId] = providerUserId.value
}

/** 상태와 정지 정보 네 열. */
fun Member.writeStatusTo(row: UpdateBuilder<*>) {
    row[MemberTable.status] = status.name
    row[MemberTable.suspendedAt] = suspendedAt?.toTimestamptz()
    row[MemberTable.suspendedUntil] = suspendedUntil?.toTimestamptz()
    row[MemberTable.suspensionReason] = suspensionReason
}

/** 새 프로필 → 행. */
fun Profile.writeNewTo(row: UpdateBuilder<*>, memberId: Long) {
    row[MemberProfileTable.memberId] = memberId
    row[MemberProfileTable.nickname] = nickname.value
    row[MemberProfileTable.profileImageUrl] = profileImageUrl
}

/** 동의 행 → 도메인. */
fun ResultRow.toAgreement(): Agreement = Agreement(
    termsId = this[MemberTermsAgreementTable.termsId],
    termsCode = this[MemberTermsAgreementTable.termsCode],
    termsVersion = this[MemberTermsAgreementTable.termsVersion],
    agreed = this[MemberTermsAgreementTable.agreed],
    agreedAt = this[MemberTermsAgreementTable.agreedAt].toInstant(),
)

/** 동의 → 행. */
fun Agreement.writeTo(row: UpdateBuilder<*>, memberId: Long) {
    row[MemberTermsAgreementTable.memberId] = memberId
    row[MemberTermsAgreementTable.termsId] = termsId
    row[MemberTermsAgreementTable.termsCode] = termsCode
    row[MemberTermsAgreementTable.termsVersion] = termsVersion
    row[MemberTermsAgreementTable.agreed] = agreed
    row[MemberTermsAgreementTable.agreedAt] = agreedAt.toTimestamptz()
}

/** 약관 행 → 도메인. */
fun ResultRow.toTerms(): Terms = Terms(
    id = this[TermsTable.id],
    code = this[TermsTable.code],
    version = this[TermsTable.version],
    required = this[TermsTable.required],
)

/** 인증 코드 행 → 도메인. 대상 주소는 [decrypt]로 푼다. */
fun ResultRow.toEmailVerification(decrypt: (String) -> String): EmailVerification = EmailVerification(
    id = this[EmailVerificationTable.id],
    publicId = this[EmailVerificationTable.publicId],
    memberId = this[EmailVerificationTable.memberId],
    codeHash = this[EmailVerificationTable.codeHash],
    expiresAt = this[EmailVerificationTable.expiresAt].toInstant(),
    attempts = this[EmailVerificationTable.attempts],
    maxAttempts = this[EmailVerificationTable.maxAttempts],
    consumed = this[EmailVerificationTable.consumed],
    verifiedAt = this[EmailVerificationTable.verifiedAt]?.toInstant(),
    createdAt = this[EmailVerificationTable.createdAt].toInstant(),
    purpose = VerificationPurpose.valueOf(this[EmailVerificationTable.purpose]),
    targetEmail = this[EmailVerificationTable.targetEmailEncrypted]?.let { Email(decrypt(it)) },
)

/** 새 인증 코드 → 행. 대상 주소의 암호문은 호출자가 넘긴다. id와 발급 시각은 DB가 채운다. */
fun EmailVerification.writeNewTo(row: UpdateBuilder<*>, encryptedTargetEmail: String?) {
    row[EmailVerificationTable.publicId] = publicId
    row[EmailVerificationTable.memberId] = memberId
    row[EmailVerificationTable.codeHash] = codeHash
    row[EmailVerificationTable.expiresAt] = expiresAt.toTimestamptz()
    row[EmailVerificationTable.maxAttempts] = maxAttempts
    row[EmailVerificationTable.purpose] = purpose.name
    row[EmailVerificationTable.targetEmailEncrypted] = encryptedTargetEmail
    writeProgressTo(row)
}

/** 확인이 바꾸는 세 열(시도 횟수·소진·통과 시각). */
fun EmailVerification.writeProgressTo(row: UpdateBuilder<*>) {
    row[EmailVerificationTable.attempts] = attempts
    row[EmailVerificationTable.consumed] = consumed
    row[EmailVerificationTable.verifiedAt] = verifiedAt?.toTimestamptz()
}

/** 재설정 코드 행 → 도메인. */
fun ResultRow.toPasswordReset(): PasswordReset = PasswordReset(
    id = this[PasswordResetTable.id],
    publicId = this[PasswordResetTable.publicId],
    memberId = this[PasswordResetTable.memberId],
    codeHash = this[PasswordResetTable.codeHash],
    expiresAt = this[PasswordResetTable.expiresAt].toInstant(),
    attempts = this[PasswordResetTable.attempts],
    maxAttempts = this[PasswordResetTable.maxAttempts],
    consumed = this[PasswordResetTable.consumed],
    createdAt = this[PasswordResetTable.createdAt].toInstant(),
)

/** 새 재설정 코드 → 행. id와 발급 시각은 DB가 채운다. */
fun PasswordReset.writeNewTo(row: UpdateBuilder<*>) {
    row[PasswordResetTable.publicId] = publicId
    row[PasswordResetTable.memberId] = memberId
    row[PasswordResetTable.codeHash] = codeHash
    row[PasswordResetTable.expiresAt] = expiresAt.toTimestamptz()
    row[PasswordResetTable.maxAttempts] = maxAttempts
    writeProgressTo(row)
}

/** 확인이 바꾸는 두 열(시도 횟수·소진). */
fun PasswordReset.writeProgressTo(row: UpdateBuilder<*>) {
    row[PasswordResetTable.attempts] = attempts
    row[PasswordResetTable.consumed] = consumed
}

/** 역할 행과 그 역할의 권한 코드 → 도메인. */
fun ResultRow.toRole(permissionCodes: Collection<String>): Role = Role(
    id = this[RoleTable.id],
    code = this[RoleTable.code],
    name = this[RoleTable.name],
    permissions = permissionCodes.map { Permission(it) }.toSet(),
)
