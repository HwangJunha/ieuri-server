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
import org.around.ieuri.auth.domain.member.Nickname
import org.around.ieuri.auth.domain.member.PasswordHash
import org.around.ieuri.auth.domain.member.PasswordReset
import org.around.ieuri.auth.domain.member.Profile
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderUserId
import org.around.ieuri.auth.domain.member.VerificationPurpose
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 회원 애그리게이트의 행 ↔ 도메인 변환(POL-033). 같은 타입 열끼리 뒤바뀌지 않게 필드마다 다른 값을 준다.
 * - 쓴 값은 내부 API인 `values`로만 읽힌다. 쓰는 열의 집합도 함께 고정해 DB가 채우는 열을 건드리지 않게 한다.
 */
@OptIn(InternalApi::class)
class MemberRowMappersTest {

    private fun row(table: Table) = InsertStatement<Number>(table)

    /** 쓴 열이 정확히 [columns]뿐인지 — DB가 채우는 열이나 이번 쓰기와 무관한 열을 건드리지 않는다. */
    private fun assertWrites(row: InsertStatement<Number>, vararg columns: Column<*>) {
        assertEquals<Set<Column<*>>>(columns.toSet(), row.values.keys)
    }

    private fun at(text: String): Instant = Instant.parse(text)

    private fun tz(text: String): OffsetDateTime = OffsetDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC)

    private val suspendedAt = "2026-10-01T01:00:00Z"
    private val suspendedUntil = "2026-10-02T02:00:00Z"

    private fun member(
        suspendedAt: Instant? = at(this.suspendedAt),
        suspendedUntil: Instant? = at(this.suspendedUntil),
        suspensionReason: String? = "사유-값",
        passwordHash: PasswordHash? = PasswordHash("hash-값"),
    ) = Member(
        id = 11L,
        provider = Provider.EMAIL,
        providerUserId = ProviderUserId("login-id-값"),
        email = Email("plain@example.com"),
        emailVerified = true,
        status = MemberStatus.SUSPENDED,
        ageVerified = false,
        suspendedAt = suspendedAt,
        suspendedUntil = suspendedUntil,
        suspensionReason = suspensionReason,
        passwordHash = passwordHash,
    )

    @Test
    fun `시각은 같은 순간의 UTC timestamptz로 옮겨진다`() {
        val converted = at("2026-10-05T03:04:05.678Z").toTimestamptz()

        assertEquals(ZoneOffset.UTC, converted.offset)
        assertEquals(at("2026-10-05T03:04:05.678Z"), converted.toInstant())
    }

    // ── 회원 ──

    @Test
    fun `회원 행이 도메인으로 옮겨진다`() {
        val member = ResultRow.createAndFillValues(
            mapOf(
                MemberTable.id to 11L,
                MemberTable.provider to "KAKAO",
                MemberTable.providerUserId to "kakao-값",
                MemberTable.emailEncrypted to "암호문-값",
                MemberTable.emailVerified to true,
                MemberTable.status to "SUSPENDED",
                MemberTable.ageVerified to false,
                MemberTable.suspendedAt to tz(suspendedAt),
                MemberTable.suspendedUntil to tz(suspendedUntil),
                MemberTable.suspensionReason to "사유-값",
                MemberTable.passwordHash to "hash-값",
            ),
        ).toMember(decryptedEmail = "plain@example.com")

        assertEquals(11L, member.id)
        assertEquals(Provider.KAKAO, member.provider)
        assertEquals("kakao-값", member.providerUserId.value)
        assertEquals("plain@example.com", member.email.value)
        assertEquals(true, member.emailVerified)
        assertEquals(MemberStatus.SUSPENDED, member.status)
        assertEquals(false, member.ageVerified)
        assertEquals(at(suspendedAt), member.suspendedAt)
        assertEquals(at(suspendedUntil), member.suspendedUntil)
        assertEquals("사유-값", member.suspensionReason)
        assertEquals("hash-값", member.passwordHash?.value)
    }

    @Test
    fun `회원 행의 null 열은 null로 옮겨지고 두 불리언은 섞이지 않는다`() {
        val member = ResultRow.createAndFillValues(
            mapOf(
                MemberTable.id to 12L,
                MemberTable.provider to "APPLE",
                MemberTable.providerUserId to "apple-값",
                MemberTable.emailVerified to false,
                MemberTable.status to "ACTIVE",
                MemberTable.ageVerified to true,
                MemberTable.suspendedAt to null,
                MemberTable.suspendedUntil to null,
                MemberTable.suspensionReason to null,
                MemberTable.passwordHash to null,
            ),
        ).toMember(decryptedEmail = "sns@example.com")

        assertEquals(Provider.APPLE, member.provider)
        assertEquals(MemberStatus.ACTIVE, member.status)
        assertEquals(false, member.emailVerified)
        assertEquals(true, member.ageVerified)
        assertNull(member.suspendedAt)
        assertNull(member.suspendedUntil)
        assertNull(member.suspensionReason)
        assertNull(member.passwordHash)
    }

    @Test
    fun `새 회원이 행으로 옮겨진다 - id와 정지 정보는 쓰지 않는다`() {
        val row = row(MemberTable)

        member().writeNewTo(row, encryptedEmail = "암호문-값", emailIdentifier = "식별자-값")

        assertEquals("EMAIL", row.values[MemberTable.provider])
        assertEquals("login-id-값", row.values[MemberTable.providerUserId])
        assertEquals("암호문-값", row.values[MemberTable.emailEncrypted])
        assertEquals("식별자-값", row.values[MemberTable.emailIdentifier])
        assertEquals(true, row.values[MemberTable.emailVerified])
        assertEquals("hash-값", row.values[MemberTable.passwordHash])
        assertEquals("SUSPENDED", row.values[MemberTable.status])
        assertEquals(false, row.values[MemberTable.ageVerified])
        assertWrites(row, MemberTable.provider, MemberTable.providerUserId, MemberTable.emailEncrypted, MemberTable.emailIdentifier, MemberTable.emailVerified, MemberTable.passwordHash, MemberTable.status, MemberTable.ageVerified)
    }

    @Test
    fun `비밀번호가 없는 새 회원은 해시 열에 null을 쓴다`() {
        val row = row(MemberTable)

        member(passwordHash = null).writeNewTo(row, encryptedEmail = "암호문-값", emailIdentifier = "식별자-값")

        assertNull(row.values[MemberTable.passwordHash])
        assertEquals(true, MemberTable.passwordHash in row.values)
    }

    @Test
    fun `이메일 교체는 암호문과 파생 식별자와 로그인 식별자 세 열만 쓴다`() {
        val row = row(MemberTable)

        member().writeEmailTo(row, encryptedEmail = "암호문-값", emailIdentifier = "식별자-값")

        assertEquals("암호문-값", row.values[MemberTable.emailEncrypted])
        assertEquals("식별자-값", row.values[MemberTable.emailIdentifier])
        assertEquals("login-id-값", row.values[MemberTable.providerUserId])
        assertWrites(row, MemberTable.emailEncrypted, MemberTable.emailIdentifier, MemberTable.providerUserId)
    }

    @Test
    fun `상태 쓰기는 상태와 정지 정보 네 열만 쓴다`() {
        val row = row(MemberTable)

        member().writeStatusTo(row)

        assertEquals("SUSPENDED", row.values[MemberTable.status])
        assertEquals(tz(suspendedAt), row.values[MemberTable.suspendedAt])
        assertEquals(tz(suspendedUntil), row.values[MemberTable.suspendedUntil])
        assertEquals("사유-값", row.values[MemberTable.suspensionReason])
        assertWrites(row, MemberTable.status, MemberTable.suspendedAt, MemberTable.suspendedUntil, MemberTable.suspensionReason)
    }

    @Test
    fun `정지 정보가 없으면 세 열에 null을 쓴다 - 복구가 이전 값을 지운다`() {
        val row = row(MemberTable)

        member(suspendedAt = null, suspendedUntil = null, suspensionReason = null).writeStatusTo(row)

        assertNull(row.values[MemberTable.suspendedAt])
        assertNull(row.values[MemberTable.suspendedUntil])
        assertNull(row.values[MemberTable.suspensionReason])
        assertWrites(row, MemberTable.status, MemberTable.suspendedAt, MemberTable.suspendedUntil, MemberTable.suspensionReason)
    }

    // ── 프로필 ──

    @Test
    fun `새 프로필이 행으로 옮겨진다`() {
        val row = row(MemberProfileTable)

        Profile(Nickname("닉네임-값"), "images/profile/21/a").writeNewTo(row, memberId = 21L)

        assertEquals(21L, row.values[MemberProfileTable.memberId])
        assertEquals("닉네임-값", row.values[MemberProfileTable.nickname])
        assertEquals("images/profile/21/a", row.values[MemberProfileTable.profileImageUrl])
        assertWrites(row, MemberProfileTable.memberId, MemberProfileTable.nickname, MemberProfileTable.profileImageUrl)
    }

    @Test
    fun `이미지가 없는 프로필은 이미지 열에 null을 쓴다`() {
        val row = row(MemberProfileTable)

        Profile(Nickname("닉네임-값"), null).writeNewTo(row, memberId = 22L)

        assertNull(row.values[MemberProfileTable.profileImageUrl])
        assertEquals("닉네임-값", row.values[MemberProfileTable.nickname])
    }

    // ── 약관·동의 ──

    @Test
    fun `동의 행이 도메인으로 옮겨진다`() {
        val agreement = ResultRow.createAndFillValues(
            mapOf(
                MemberTermsAgreementTable.id to 31L,
                MemberTermsAgreementTable.memberId to 32L,
                MemberTermsAgreementTable.termsId to 33L,
                MemberTermsAgreementTable.termsCode to "PRIVACY",
                MemberTermsAgreementTable.termsVersion to "2.1",
                MemberTermsAgreementTable.agreed to true,
                MemberTermsAgreementTable.agreedAt to tz("2026-09-01T09:00:00Z"),
            ),
        ).toAgreement()

        assertEquals(33L, agreement.termsId)
        assertEquals("PRIVACY", agreement.termsCode)
        assertEquals("2.1", agreement.termsVersion)
        assertEquals(true, agreement.agreed)
        assertEquals(at("2026-09-01T09:00:00Z"), agreement.agreedAt)
    }

    @Test
    fun `동의가 행으로 옮겨진다 - 회원 id와 약관 id가 섞이지 않는다`() {
        val row = row(MemberTermsAgreementTable)

        Agreement(termsId = 41L, termsCode = "MARKETING", termsVersion = "3.2", agreed = false, agreedAt = at("2026-09-02T09:00:00Z")).writeTo(row, memberId = 42L)

        assertEquals(42L, row.values[MemberTermsAgreementTable.memberId])
        assertEquals(41L, row.values[MemberTermsAgreementTable.termsId])
        assertEquals("MARKETING", row.values[MemberTermsAgreementTable.termsCode])
        assertEquals("3.2", row.values[MemberTermsAgreementTable.termsVersion])
        assertEquals(false, row.values[MemberTermsAgreementTable.agreed])
        assertEquals(tz("2026-09-02T09:00:00Z"), row.values[MemberTermsAgreementTable.agreedAt])
        assertEquals(false, MemberTermsAgreementTable.id in row.values)
    }

    @Test
    fun `약관 행이 도메인으로 옮겨진다`() {
        val terms = ResultRow.createAndFillValues(
            mapOf(
                TermsTable.id to 51L,
                TermsTable.code to "LOCATION",
                TermsTable.title to "제목-값",
                TermsTable.version to "4.3",
                TermsTable.required to true,
            ),
        ).toTerms()

        assertEquals(51L, terms.id)
        assertEquals("LOCATION", terms.code)
        assertEquals("4.3", terms.version)
        assertEquals(true, terms.required)
    }

    // ── 인증 코드 ──

    private val verificationPublicId = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444")

    private fun verification(verifiedAt: Instant? = at("2026-10-05T03:05:00Z"), targetEmail: Email? = Email("target@example.com")) = EmailVerification(
        id = 61L,
        publicId = verificationPublicId,
        memberId = 62L,
        codeHash = "코드-해시-값",
        expiresAt = at("2026-10-05T03:10:00Z"),
        attempts = 2,
        maxAttempts = 5,
        consumed = true,
        verifiedAt = verifiedAt,
        createdAt = at("2026-10-05T03:00:00Z"),
        purpose = VerificationPurpose.EMAIL_CHANGE,
        targetEmail = targetEmail,
    )

    @Test
    fun `인증 코드 행이 도메인으로 옮겨지고 대상 주소는 암호문을 풀어 담는다`() {
        val decryptedFrom = mutableListOf<String>()
        val verification = ResultRow.createAndFillValues(
            mapOf(
                EmailVerificationTable.id to 61L,
                EmailVerificationTable.publicId to verificationPublicId,
                EmailVerificationTable.memberId to 62L,
                EmailVerificationTable.codeHash to "코드-해시-값",
                EmailVerificationTable.expiresAt to tz("2026-10-05T03:10:00Z"),
                EmailVerificationTable.attempts to 2,
                EmailVerificationTable.maxAttempts to 5,
                EmailVerificationTable.consumed to true,
                EmailVerificationTable.verifiedAt to tz("2026-10-05T03:05:00Z"),
                EmailVerificationTable.createdAt to tz("2026-10-05T03:00:00Z"),
                EmailVerificationTable.purpose to "EMAIL_CHANGE",
                EmailVerificationTable.targetEmailEncrypted to "대상-암호문",
            ),
        ).toEmailVerification { cipher ->
            decryptedFrom += cipher
            "target@example.com"
        }

        assertEquals(61L, verification.id)
        assertEquals(verificationPublicId, verification.publicId)
        assertEquals(62L, verification.memberId)
        assertEquals("코드-해시-값", verification.codeHash)
        assertEquals(at("2026-10-05T03:10:00Z"), verification.expiresAt)
        assertEquals(2, verification.attempts)
        assertEquals(5, verification.maxAttempts)
        assertEquals(true, verification.consumed)
        assertEquals(at("2026-10-05T03:05:00Z"), verification.verifiedAt)
        assertEquals(at("2026-10-05T03:00:00Z"), verification.createdAt)
        assertEquals(VerificationPurpose.EMAIL_CHANGE, verification.purpose)
        assertEquals("target@example.com", verification.targetEmail?.value)
        assertEquals(listOf("대상-암호문"), decryptedFrom)
    }

    @Test
    fun `대상 주소와 통과 시각이 없는 인증 코드 행은 null로 옮겨지고 복호화를 부르지 않는다`() {
        var decryptCalls = 0
        val verification = ResultRow.createAndFillValues(
            mapOf(
                EmailVerificationTable.id to 63L,
                EmailVerificationTable.publicId to verificationPublicId,
                EmailVerificationTable.memberId to 64L,
                EmailVerificationTable.codeHash to "코드-해시-값",
                EmailVerificationTable.expiresAt to tz("2026-10-05T03:10:00Z"),
                EmailVerificationTable.attempts to 0,
                EmailVerificationTable.maxAttempts to 5,
                EmailVerificationTable.consumed to false,
                EmailVerificationTable.verifiedAt to null,
                EmailVerificationTable.createdAt to tz("2026-10-05T03:00:00Z"),
                EmailVerificationTable.purpose to "REGISTRATION",
                EmailVerificationTable.targetEmailEncrypted to null,
            ),
        ).toEmailVerification {
            decryptCalls++
            "unused@example.com"
        }

        assertEquals(VerificationPurpose.REGISTRATION, verification.purpose)
        assertEquals(false, verification.consumed)
        assertNull(verification.verifiedAt)
        assertNull(verification.targetEmail)
        assertEquals(0, decryptCalls)
    }

    @Test
    fun `새 인증 코드가 행으로 옮겨진다 - id와 발급 시각은 쓰지 않는다`() {
        val row = row(EmailVerificationTable)

        verification().writeNewTo(row, encryptedTargetEmail = "대상-암호문")

        assertEquals(verificationPublicId, row.values[EmailVerificationTable.publicId])
        assertEquals(62L, row.values[EmailVerificationTable.memberId])
        assertEquals("코드-해시-값", row.values[EmailVerificationTable.codeHash])
        assertEquals(tz("2026-10-05T03:10:00Z"), row.values[EmailVerificationTable.expiresAt])
        assertEquals(5, row.values[EmailVerificationTable.maxAttempts])
        assertEquals("EMAIL_CHANGE", row.values[EmailVerificationTable.purpose])
        assertEquals("대상-암호문", row.values[EmailVerificationTable.targetEmailEncrypted])
        assertEquals(2, row.values[EmailVerificationTable.attempts])
        assertEquals(true, row.values[EmailVerificationTable.consumed])
        assertEquals(tz("2026-10-05T03:05:00Z"), row.values[EmailVerificationTable.verifiedAt])
        assertEquals(false, EmailVerificationTable.id in row.values)
        assertEquals(false, EmailVerificationTable.createdAt in row.values)
    }

    @Test
    fun `대상 주소와 통과 시각이 없는 새 인증 코드는 두 열에 null을 쓴다`() {
        val row = row(EmailVerificationTable)

        verification(verifiedAt = null, targetEmail = Email("ignored@example.com")).writeNewTo(row, encryptedTargetEmail = null)

        assertNull(row.values[EmailVerificationTable.targetEmailEncrypted])
        assertNull(row.values[EmailVerificationTable.verifiedAt])
        assertEquals(true, EmailVerificationTable.targetEmailEncrypted in row.values)
    }

    @Test
    fun `인증 코드 확인은 시도 횟수와 소진과 통과 시각 세 열만 쓴다`() {
        val row = row(EmailVerificationTable)

        verification().writeProgressTo(row)

        assertEquals(2, row.values[EmailVerificationTable.attempts])
        assertEquals(true, row.values[EmailVerificationTable.consumed])
        assertEquals(tz("2026-10-05T03:05:00Z"), row.values[EmailVerificationTable.verifiedAt])
        assertWrites(row, EmailVerificationTable.attempts, EmailVerificationTable.consumed, EmailVerificationTable.verifiedAt)
    }

    // ── 재설정 코드 ──

    private val resetPublicId = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444")

    private val reset = PasswordReset(
        id = 71L,
        publicId = resetPublicId,
        memberId = 72L,
        codeHash = "재설정-해시-값",
        expiresAt = at("2026-10-06T04:10:00Z"),
        attempts = 3,
        maxAttempts = 6,
        consumed = true,
        createdAt = at("2026-10-06T04:00:00Z"),
    )

    @Test
    fun `재설정 코드 행이 도메인으로 옮겨진다`() {
        val read = ResultRow.createAndFillValues(
            mapOf(
                PasswordResetTable.id to 71L,
                PasswordResetTable.publicId to resetPublicId,
                PasswordResetTable.memberId to 72L,
                PasswordResetTable.codeHash to "재설정-해시-값",
                PasswordResetTable.expiresAt to tz("2026-10-06T04:10:00Z"),
                PasswordResetTable.attempts to 3,
                PasswordResetTable.maxAttempts to 6,
                PasswordResetTable.consumed to true,
                PasswordResetTable.createdAt to tz("2026-10-06T04:00:00Z"),
            ),
        ).toPasswordReset()

        assertEquals(71L, read.id)
        assertEquals(resetPublicId, read.publicId)
        assertEquals(72L, read.memberId)
        assertEquals("재설정-해시-값", read.codeHash)
        assertEquals(at("2026-10-06T04:10:00Z"), read.expiresAt)
        assertEquals(3, read.attempts)
        assertEquals(6, read.maxAttempts)
        assertEquals(true, read.consumed)
        assertEquals(at("2026-10-06T04:00:00Z"), read.createdAt)
    }

    @Test
    fun `새 재설정 코드가 행으로 옮겨진다 - id와 발급 시각은 쓰지 않는다`() {
        val row = row(PasswordResetTable)

        reset.writeNewTo(row)

        assertEquals(resetPublicId, row.values[PasswordResetTable.publicId])
        assertEquals(72L, row.values[PasswordResetTable.memberId])
        assertEquals("재설정-해시-값", row.values[PasswordResetTable.codeHash])
        assertEquals(tz("2026-10-06T04:10:00Z"), row.values[PasswordResetTable.expiresAt])
        assertEquals(6, row.values[PasswordResetTable.maxAttempts])
        assertEquals(3, row.values[PasswordResetTable.attempts])
        assertEquals(true, row.values[PasswordResetTable.consumed])
        assertEquals(false, PasswordResetTable.id in row.values)
        assertEquals(false, PasswordResetTable.createdAt in row.values)
    }

    @Test
    fun `재설정 코드 확인은 시도 횟수와 소진 두 열만 쓴다`() {
        val row = row(PasswordResetTable)

        reset.writeProgressTo(row)

        assertEquals(3, row.values[PasswordResetTable.attempts])
        assertEquals(true, row.values[PasswordResetTable.consumed])
        assertWrites(row, PasswordResetTable.attempts, PasswordResetTable.consumed)
    }

    // ── 역할 ──

    @Test
    fun `역할 행과 권한 코드가 도메인으로 옮겨진다`() {
        val role = ResultRow.createAndFillValues(
            mapOf(
                RoleTable.id to 81L,
                RoleTable.code to "ADMIN",
                RoleTable.name to "관리자-이름",
                RoleTable.description to "설명-값",
            ),
        ).toRole(listOf("role:assign", "member:read", "role:assign"))

        assertEquals(81L, role.id)
        assertEquals("ADMIN", role.code)
        assertEquals("관리자-이름", role.name)
        assertEquals(setOf("role:assign", "member:read"), role.permissions.map { it.code }.toSet())
        assertEquals(2, role.permissions.size)
    }

    @Test
    fun `권한이 없는 역할은 빈 권한으로 옮겨진다`() {
        val role = ResultRow.createAndFillValues(
            mapOf(RoleTable.id to 82L, RoleTable.code to "USER", RoleTable.name to "사용자-이름"),
        ).toRole(emptyList())

        assertEquals("USER", role.code)
        assertEquals(emptySet(), role.permissions)
    }
}
