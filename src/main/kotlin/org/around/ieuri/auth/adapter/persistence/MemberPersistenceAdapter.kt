package org.around.ieuri.auth.adapter.persistence

import org.around.ieuri.auth.adapter.persistence.mapper.toAgreement
import org.around.ieuri.auth.adapter.persistence.mapper.toEmailVerification
import org.around.ieuri.auth.adapter.persistence.mapper.toMember
import org.around.ieuri.auth.adapter.persistence.mapper.toPasswordReset
import org.around.ieuri.auth.adapter.persistence.mapper.toRole
import org.around.ieuri.auth.adapter.persistence.mapper.toTerms
import org.around.ieuri.auth.adapter.persistence.mapper.toTimestamptz
import org.around.ieuri.auth.adapter.persistence.mapper.writeEmailTo
import org.around.ieuri.auth.adapter.persistence.mapper.writeNewTo
import org.around.ieuri.auth.adapter.persistence.mapper.writeProgressTo
import org.around.ieuri.auth.adapter.persistence.mapper.writeStatusTo
import org.around.ieuri.auth.adapter.persistence.mapper.writeTo
import org.around.ieuri.auth.adapter.persistence.table.EmailVerificationTable
import org.around.ieuri.auth.adapter.persistence.table.MemberProfileTable
import org.around.ieuri.auth.adapter.persistence.table.MemberRoleTable
import org.around.ieuri.auth.adapter.persistence.table.MemberTable
import org.around.ieuri.auth.adapter.persistence.table.MemberTermsAgreementTable
import org.around.ieuri.auth.adapter.persistence.table.PasswordResetTable
import org.around.ieuri.auth.adapter.persistence.table.PermissionTable
import org.around.ieuri.auth.adapter.persistence.table.RolePermissionTable
import org.around.ieuri.auth.adapter.persistence.table.RoleTable
import org.around.ieuri.auth.adapter.persistence.table.TermsTable
import org.around.ieuri.auth.application.member.required.EmailEncryptorPort
import org.around.ieuri.auth.application.member.required.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.Agreement
import org.around.ieuri.auth.domain.member.EmailVerification
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.MemberRoleChange
import org.around.ieuri.auth.domain.member.Nickname
import org.around.ieuri.auth.domain.member.PasswordReset
import org.around.ieuri.auth.domain.member.Profile
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderRefreshToken
import org.around.ieuri.auth.domain.member.ProviderUserId
import org.around.ieuri.auth.domain.member.Role
import org.around.ieuri.auth.domain.member.Terms
import org.around.ieuri.auth.domain.member.VerificationPurpose
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/** 회원 저장소 구현. 이메일·provider 토큰·인증 대상 주소는 여기서 암호화해 쓴다. */
@Repository
class MemberPersistenceAdapter(
    private val encryptor: EmailEncryptorPort,
    private val identifierProtector: LoginIdentifierProtectorPort,
) : MemberRepository {

    override fun findById(memberId: Long): Member? = MemberTable.selectAll()
        .where { MemberTable.id eq memberId }
        .singleOrNull()
        ?.toDecryptedMember()

    override fun findByProviderUserId(provider: Provider, providerUserId: ProviderUserId): Member? = MemberTable.selectAll()
        .where { (MemberTable.provider eq provider.name) and (MemberTable.providerUserId eq providerUserId.value) }
        .singleOrNull()
        ?.toDecryptedMember()

    override fun existsByProviderUserId(provider: Provider, providerUserId: ProviderUserId): Boolean = MemberTable.select(MemberTable.id)
        .where { (MemberTable.provider eq provider.name) and (MemberTable.providerUserId eq providerUserId.value) }
        .limit(1)
        .any()

    override fun existsByEmailIdentifier(provider: Provider, emailIdentifier: String, excludingMemberId: Long?): Boolean {
        val sameEmail = (MemberTable.provider eq provider.name) and (MemberTable.emailIdentifier eq emailIdentifier)
        return MemberTable.select(MemberTable.id)
            .where { excludingMemberId?.let { sameEmail and (MemberTable.id neq it) } ?: sameEmail }
            .limit(1)
            .any()
    }

    /** 회원을 먼저 넣어 id를 받은 뒤 프로필·동의를 붙인다(FK 순서). */
    override fun insert(member: Member, profile: Profile, agreements: List<Agreement>): Long {
        val memberId = MemberTable.insertReturning(listOf(MemberTable.id)) {
            member.writeNewTo(it, encryptor.encrypt(member.email.value), identifierProtector.protect(member.email.value))
        }.single()[MemberTable.id]
        MemberProfileTable.insert { profile.writeNewTo(it, memberId) }
        MemberTermsAgreementTable.batchInsert(agreements, shouldReturnGeneratedValues = false) { it.writeTo(this, memberId) }
        return memberId
    }

    override fun saveEmailVerified(member: Member) {
        updateMember(member) { it[MemberTable.emailVerified] = member.emailVerified }
    }

    override fun saveEmail(member: Member) {
        updateMember(member) {
            member.writeEmailTo(it, encryptor.encrypt(member.email.value), identifierProtector.protect(member.email.value))
        }
    }

    override fun savePassword(member: Member) {
        updateMember(member) { it[MemberTable.passwordHash] = member.passwordHash?.value }
    }

    override fun saveStatus(member: Member) {
        updateMember(member) { member.writeStatusTo(it) }
    }

    override fun saveProviderRefreshToken(token: ProviderRefreshToken) {
        MemberTable.update({ MemberTable.id eq token.memberId }) {
            it[MemberTable.providerRefreshTokenEncrypted] = encryptor.encrypt(token.value)
            it[MemberTable.updatedAt] = CurrentTimestampWithTimeZone
        }
    }

    override fun findProviderRefreshToken(memberId: Long): String? = MemberTable.select(MemberTable.providerRefreshTokenEncrypted)
        .where { MemberTable.id eq memberId }
        .singleOrNull()
        ?.get(MemberTable.providerRefreshTokenEncrypted)
        ?.let { encryptor.decrypt(it) }

    override fun saveNickname(memberId: Long, nickname: Nickname): Boolean = MemberProfileTable.update({ MemberProfileTable.memberId eq memberId }) {
        it[MemberProfileTable.nickname] = nickname.value
        it[MemberProfileTable.updatedAt] = CurrentTimestampWithTimeZone
    } > 0

    override fun saveProfileImage(memberId: Long, objectKey: String?): Boolean = MemberProfileTable.update({ MemberProfileTable.memberId eq memberId }) {
        it[MemberProfileTable.profileImageUrl] = objectKey
        it[MemberProfileTable.updatedAt] = CurrentTimestampWithTimeZone
    } > 0

    override fun findProfileImageKey(memberId: Long): String? = MemberProfileTable.select(MemberProfileTable.profileImageUrl)
        .where { MemberProfileTable.memberId eq memberId }
        .singleOrNull()
        ?.get(MemberProfileTable.profileImageUrl)

    /** 순서를 바꾸면 안 된다 — 자식을 하나라도 남기면 그 회원의 탈퇴가 FK에 막힌다. 새 자식 테이블은 여기에도 넣는다. */
    override fun delete(memberId: Long) {
        EmailVerificationTable.deleteWhere { EmailVerificationTable.memberId eq memberId }
        PasswordResetTable.deleteWhere { PasswordResetTable.memberId eq memberId }
        MemberTermsAgreementTable.deleteWhere { MemberTermsAgreementTable.memberId eq memberId }
        MemberProfileTable.deleteWhere { MemberProfileTable.memberId eq memberId }
        MemberRoleTable.deleteWhere { MemberRoleTable.memberId eq memberId }
        MemberTable.deleteWhere { MemberTable.id eq memberId }
    }

    override fun insertEmailVerification(verification: EmailVerification) {
        EmailVerificationTable.insert {
            verification.writeNewTo(it, verification.targetEmail?.let { email -> encryptor.encrypt(email.value) })
        }
    }

    override fun saveEmailVerification(verification: EmailVerification) {
        EmailVerificationTable.update({ EmailVerificationTable.publicId eq verification.publicId }) { verification.writeProgressTo(it) }
    }

    override fun consumeEmailVerification(verification: EmailVerification) {
        EmailVerificationTable.update({ EmailVerificationTable.publicId eq verification.publicId }) { it[EmailVerificationTable.consumed] = true }
    }

    override fun findEmailVerification(publicId: UUID, forUpdate: Boolean): EmailVerification? = EmailVerificationTable.selectAll()
        .where { EmailVerificationTable.publicId eq publicId }
        .let { if (forUpdate) it.forUpdate() else it }
        .singleOrNull()
        ?.toEmailVerification(encryptor::decrypt)

    override fun findEmailVerifications(memberId: Long, purpose: VerificationPurpose): List<EmailVerification> = EmailVerificationTable.selectAll()
        .where { (EmailVerificationTable.memberId eq memberId) and (EmailVerificationTable.purpose eq purpose.name) }
        .orderBy(EmailVerificationTable.createdAt to SortOrder.DESC)
        .map { it.toEmailVerification(encryptor::decrypt) }

    override fun insertPasswordReset(reset: PasswordReset) {
        PasswordResetTable.insert { reset.writeNewTo(it) }
    }

    override fun savePasswordReset(reset: PasswordReset) {
        PasswordResetTable.update({ PasswordResetTable.publicId eq reset.publicId }) { reset.writeProgressTo(it) }
    }

    override fun consumePasswordReset(reset: PasswordReset) {
        PasswordResetTable.update({ PasswordResetTable.publicId eq reset.publicId }) { it[PasswordResetTable.consumed] = true }
    }

    override fun findPasswordResets(memberId: Long, forUpdate: Boolean): List<PasswordReset> = PasswordResetTable.selectAll()
        .where { PasswordResetTable.memberId eq memberId }
        .orderBy(PasswordResetTable.createdAt to SortOrder.DESC)
        .let { if (forUpdate) it.forUpdate() else it }
        .map { it.toPasswordReset() }

    override fun findRole(code: String): Role? {
        val row = RoleTable.selectAll().where { RoleTable.code eq code }.singleOrNull() ?: return null
        val roleId = row[RoleTable.id]
        return row.toRole(permissionCodesOf(listOf(roleId))[roleId].orEmpty())
    }

    override fun findRolesOf(memberId: Long): List<Role> {
        val rows = MemberRoleTable
            .join(RoleTable, JoinType.INNER, MemberRoleTable.roleId, RoleTable.id)
            .select(RoleTable.columns)
            .where { MemberRoleTable.memberId eq memberId }
            .toList()
        if (rows.isEmpty()) return emptyList()
        val permissionCodes = permissionCodesOf(rows.map { it[RoleTable.id] })
        return rows.map { it.toRole(permissionCodes[it[RoleTable.id]].orEmpty()) }
    }

    override fun addRole(change: MemberRoleChange.Granted, assignedAt: Instant) {
        MemberRoleTable.insertIgnore {
            it[MemberRoleTable.memberId] = change.memberId
            it[MemberRoleTable.roleId] = change.role.id
            it[MemberRoleTable.assignedAt] = assignedAt.toTimestamptz()
        }
    }

    override fun removeRole(change: MemberRoleChange.Revoked) {
        MemberRoleTable.deleteWhere { (MemberRoleTable.memberId eq change.memberId) and (MemberRoleTable.roleId eq change.role.id) }
    }

    override fun findEffectiveTerms(now: Instant): List<Terms> = TermsTable.selectAll()
        .where { TermsTable.effectiveAt lessEq now.toTimestamptz() }
        .map { it.toTerms() }

    override fun findAgreements(memberId: Long): List<Agreement> = MemberTermsAgreementTable.selectAll()
        .where { MemberTermsAgreementTable.memberId eq memberId }
        .map { it.toAgreement() }

    /** 약관 버전이 오르면 다른 terms_id를 쓰므로 코드·버전은 고치지 않는다. */
    override fun saveAgreements(memberId: Long, agreements: List<Agreement>) {
        agreements.forEach { agreement ->
            MemberTermsAgreementTable.upsert(
                MemberTermsAgreementTable.memberId,
                MemberTermsAgreementTable.termsId,
                onUpdate = {
                    it[MemberTermsAgreementTable.agreed] = agreement.agreed
                    it[MemberTermsAgreementTable.agreedAt] = agreement.agreedAt.toTimestamptz()
                },
            ) { agreement.writeTo(it, memberId) }
        }
    }

    private fun ResultRow.toDecryptedMember(): Member = toMember(encryptor.decrypt(this[MemberTable.emailEncrypted]))

    /** 회원 행의 일부 열을 고치고 `updated_at`을 함께 갱신한다. 저장 전 회원이면 아무것도 하지 않는다. */
    private fun updateMember(member: Member, body: (UpdateStatement) -> Unit) {
        val id = member.id ?: return
        MemberTable.update({ MemberTable.id eq id }) {
            body(it)
            it[MemberTable.updatedAt] = CurrentTimestampWithTimeZone
        }
    }

    /** 역할별 권한 코드를 한 번에 읽는다. */
    private fun permissionCodesOf(roleIds: Collection<Long>): Map<Long, List<String>> = RolePermissionTable
        .join(PermissionTable, JoinType.INNER, RolePermissionTable.permissionId, PermissionTable.id)
        .select(RolePermissionTable.roleId, PermissionTable.code)
        .where { RolePermissionTable.roleId inList roleIds }
        .groupBy({ it[RolePermissionTable.roleId] }, { it[PermissionTable.code] })
}
