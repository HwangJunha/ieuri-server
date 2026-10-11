package org.around.ieuri.auth.application.member.required

import org.around.ieuri.auth.domain.member.Agreement
import org.around.ieuri.auth.domain.member.EmailVerification
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.MemberRoleChange
import org.around.ieuri.auth.domain.member.Nickname
import org.around.ieuri.auth.domain.member.PasswordReset
import org.around.ieuri.auth.domain.member.Profile
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderUserId
import org.around.ieuri.auth.domain.member.Role
import org.around.ieuri.auth.domain.member.Terms
import org.around.ieuri.auth.domain.member.VerificationPurpose
import java.time.Instant
import java.util.UUID

/**
 * 회원 저장소. 연산마다 쓰는 열을 나눠, 루트가 판정한 뒤 바뀐 것만 쓴다.
 * - 이메일은 암호문과 유니크 판정용 식별자를 늘 함께 쓴다.
 * - `updated_at`은 회원·프로필 행을 고칠 때마다 갱신한다.
 */
interface MemberRepository {
    /** 회원 한 행만 읽는다. 없으면 null. */
    fun findById(memberId: Long): Member?

    fun findByProviderUserId(provider: Provider, providerUserId: ProviderUserId): Member?

    fun existsByProviderUserId(provider: Provider, providerUserId: ProviderUserId): Boolean

    /** 같은 provider에 그 이메일 식별자를 가진 다른 회원이 있는지. [excludingMemberId]는 본인 제외 기준이다. */
    fun existsByEmailIdentifier(provider: Provider, emailIdentifier: String, excludingMemberId: Long?): Boolean

    /** 새 회원과 그 프로필·동의를 넣고 id를 돌려준다. */
    fun insert(member: Member, profile: Profile, agreements: List<Agreement>): Long

    fun saveEmailVerified(member: Member)

    /** 이메일과 로그인 식별자를 함께 쓴다. 경합하는 두 확정 중 하나는 유니크 제약에 걸린다. */
    fun saveEmail(member: Member)

    fun savePassword(member: Member)

    /** 상태와 정지 시각·기간·사유를 쓴다. */
    fun saveStatus(member: Member)

    /** 회원이 없으면 아무것도 하지 않는다. */
    fun saveProviderRefreshToken(memberId: Long, token: String)

    fun findProviderRefreshToken(memberId: Long): String?

    /** 프로필 행이 없으면 false. */
    fun saveNickname(memberId: Long, nickname: Nickname): Boolean

    /** null이면 이미지를 뗀다. 프로필 행이 없으면 false. */
    fun saveProfileImage(memberId: Long, objectKey: String?): Boolean

    fun findProfileImageKey(memberId: Long): String?

    /**
     * 회원과 소유 행(인증 코드·재설정 코드·동의·프로필·역할)을 지운다. 감사 기록은 남는다.
     * - 회원을 참조하는 자식을 먼저 지워야 한다(FK에 ON DELETE가 없다).
     */
    fun delete(memberId: Long)

    // 인증 코드

    fun insertEmailVerification(verification: EmailVerification)

    /** 시도 횟수·소진·통과 시각만 쓴다. */
    fun saveEmailVerification(verification: EmailVerification)

    /** 폐기 — `consumed` 한 열만 쓴다. 그 사이 통과한 코드의 통과 시각을 덮어쓰지 않는다. */
    fun consumeEmailVerification(verification: EmailVerification)

    /** [forUpdate]면 행을 잠근다 — 같은 코드의 동시 확인이 시도 횟수를 덮어쓰지 않게 한다. */
    fun findEmailVerification(publicId: UUID, forUpdate: Boolean = false): EmailVerification?

    /** 그 용도의 코드 전부(소진된 것 포함). */
    fun findEmailVerifications(memberId: Long, purpose: VerificationPurpose): List<EmailVerification>

    fun insertPasswordReset(reset: PasswordReset)

    /** 시도 횟수·소진만 쓴다. */
    fun savePasswordReset(reset: PasswordReset)

    /** 폐기 — `consumed` 한 열만 쓴다. */
    fun consumePasswordReset(reset: PasswordReset)

    /** 재설정 코드 전부(소진된 것 포함). [forUpdate]면 행을 잠근다. */
    fun findPasswordResets(memberId: Long, forUpdate: Boolean = false): List<PasswordReset>

    // 역할

    fun findRole(code: String): Role?

    fun findRolesOf(memberId: Long): List<Role>

    /** 이미 있으면 넣지 않는다. */
    fun addRole(change: MemberRoleChange.Granted, assignedAt: Instant)

    fun removeRole(change: MemberRoleChange.Revoked)

    // 약관 동의

    /** 시행일이 [now] 이전인 약관 전부(모든 버전). */
    fun findEffectiveTerms(now: Instant): List<Terms>

    fun findAgreements(memberId: Long): List<Agreement>

    /** `(회원, 약관)`당 한 행이다. 있으면 동의 여부·시각을 고치고 없으면 넣는다. */
    fun saveAgreements(memberId: Long, agreements: List<Agreement>)
}
