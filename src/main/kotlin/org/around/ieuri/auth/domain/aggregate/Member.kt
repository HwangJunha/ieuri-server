package org.around.ieuri.auth.domain.aggregate

import org.around.ieuri.auth.domain.entity.Profile
import org.around.ieuri.auth.domain.vo.Agreement
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Nickname
import org.around.ieuri.auth.domain.vo.PasswordHash
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.ProviderUserId
import java.time.Instant

/**
 * 회원 애그리거트 루트. 가입 불변식(필수 약관 동의 / 만 14세 이상 / 이메일 필수)과
 * 상태 전이(정지/복구) 불변식을 이곳에서 강제한다. Profile과 동의 이력(Agreement)을 소유한다.
 *
 * 탈퇴는 상태 전이가 아니라 행 삭제(hard delete)이므로 도메인 상태로 남기지 않는다.
 */
class Member private constructor(
    val id: Long?,
    val provider: Provider,
    val providerUserId: ProviderUserId,
    val email: Email,
    emailVerifiedInitial: Boolean,
    statusInitial: MemberStatus,
    val ageVerified: Boolean,
    suspendedAtInitial: Instant?,
    suspendedUntilInitial: Instant?,
    suspensionReasonInitial: String?,
    val profile: Profile,
    val agreements: List<Agreement>,
    /** 자체(EMAIL) 가입 회원의 비밀번호 해시. SNS 회원은 null. */
    val passwordHash: PasswordHash?,
    val createdAt: Instant,
) {
    var emailVerified: Boolean = emailVerifiedInitial
        private set
    var status: MemberStatus = statusInitial
        private set
    var suspendedAt: Instant? = suspendedAtInitial
        private set
    var suspendedUntil: Instant? = suspendedUntilInitial
        private set
    var suspensionReason: String? = suspensionReasonInitial
        private set

    /** 이메일 검증 완료 시 호출. 코드 검증 성공(EmailVerification.verify)의 결과로만 전이된다. */
    fun markEmailVerified() {
        emailVerified = true
    }

    /** 관리자 정지. ACTIVE에서 정지하거나, 이미 SUSPENDED면 사유·기간을 갱신한다. */
    fun suspend(reason: String, until: Instant?, now: Instant) {
        require(reason.isNotBlank()) { "정지 사유는 필수입니다." }
        require(until == null || until.isAfter(now)) { "정지 만료일은 현재 이후여야 합니다." }
        status = MemberStatus.SUSPENDED
        suspendedAt = now
        suspendedUntil = until
        suspensionReason = reason
    }

    /** 복구. 정지 상태에서만 ACTIVE로 되돌린다. */
    fun reactivate(now: Instant) {
        require(status == MemberStatus.SUSPENDED) { "정지 상태에서만 복구할 수 있습니다." }
        status = MemberStatus.ACTIVE
        suspendedAt = null
        suspendedUntil = null
        suspensionReason = null
    }

    /** 기간제 정지가 만료됐는지(로그인 시 자동 복구 판정). 무기한 정지(until=null)는 만료되지 않는다. */
    fun isSuspensionExpired(now: Instant): Boolean =
        status == MemberStatus.SUSPENDED && suspendedUntil != null && !now.isBefore(suspendedUntil)

    companion object {
        fun register(
            provider: Provider,
            providerUserId: ProviderUserId,
            submittedEmail: Email,
            providerEmail: Email?,
            providerEmailVerified: Boolean,
            nickname: Nickname,
            profileImageUrl: String?,
            ageVerified: Boolean,
            agreements: List<Agreement>,
            requiredTermsCodes: Set<String>,
            now: Instant,
        ): Member {
            require(ageVerified) { "만 14세 미만은 가입할 수 없습니다." }

            val agreedCodes = agreements.filter { it.agreed }.map { it.termsCode }.toSet()
            val missing = requiredTermsCodes - agreedCodes
            require(missing.isEmpty()) { "필수 약관에 동의해야 가입할 수 있습니다. 미동의: $missing" }

            val emailVerified = providerEmailVerified &&
                providerEmail != null &&
                providerEmail == submittedEmail

            return Member(
                id = null,
                provider = provider,
                providerUserId = providerUserId,
                email = submittedEmail,
                emailVerifiedInitial = emailVerified,
                statusInitial = MemberStatus.ACTIVE,
                ageVerified = ageVerified,
                suspendedAtInitial = null,
                suspendedUntilInitial = null,
                suspensionReasonInitial = null,
                profile = Profile(nickname = nickname, profileImageUrl = profileImageUrl),
                agreements = agreements,
                passwordHash = null,
                createdAt = now,
            )
        }

        /**
         * 자체(EMAIL) 회원가입. provider=EMAIL, provider_user_id=정규화된 이메일, 비밀번호 해시를 보관한다.
         * 이메일 인증 전이므로 emailVerified=false로 시작하고, 인증 코드 검증 시 활성화된다.
         * 필수 약관 동의·만 14세 불변식은 SNS 가입과 동일하게 강제한다.
         */
        fun registerByEmail(
            email: Email,
            passwordHash: PasswordHash,
            nickname: Nickname,
            ageVerified: Boolean,
            agreements: List<Agreement>,
            requiredTermsCodes: Set<String>,
            now: Instant,
        ): Member {
            require(ageVerified) { "만 14세 미만은 가입할 수 없습니다." }

            val agreedCodes = agreements.filter { it.agreed }.map { it.termsCode }.toSet()
            val missing = requiredTermsCodes - agreedCodes
            require(missing.isEmpty()) { "필수 약관에 동의해야 가입할 수 있습니다. 미동의: $missing" }

            return Member(
                id = null,
                provider = Provider.EMAIL,
                providerUserId = ProviderUserId(email.value),
                email = email,
                emailVerifiedInitial = false,
                statusInitial = MemberStatus.ACTIVE,
                ageVerified = ageVerified,
                suspendedAtInitial = null,
                suspendedUntilInitial = null,
                suspensionReasonInitial = null,
                profile = Profile(nickname = nickname, profileImageUrl = null),
                agreements = agreements,
                passwordHash = passwordHash,
                createdAt = now,
            )
        }

        /**
         * 영속 상태에서 도메인 모델 재구성(매퍼/상태 변경 전용).
         * 동의 이력은 append-only 기록이라 애그리거트 상태로 재적재하지 않는다(빈 목록).
         */
        fun reconstitute(
            id: Long,
            provider: Provider,
            providerUserId: ProviderUserId,
            email: Email,
            emailVerified: Boolean,
            status: MemberStatus,
            ageVerified: Boolean,
            suspendedAt: Instant?,
            suspendedUntil: Instant?,
            suspensionReason: String?,
            nickname: Nickname,
            profileImageUrl: String?,
            createdAt: Instant,
        ): Member = Member(
            id = id,
            provider = provider,
            providerUserId = providerUserId,
            email = email,
            emailVerifiedInitial = emailVerified,
            statusInitial = status,
            ageVerified = ageVerified,
            suspendedAtInitial = suspendedAt,
            suspendedUntilInitial = suspendedUntil,
            suspensionReasonInitial = suspensionReason,
            profile = Profile(nickname = nickname, profileImageUrl = profileImageUrl),
            agreements = emptyList(),
            passwordHash = null,
            createdAt = createdAt,
        )
    }
}
