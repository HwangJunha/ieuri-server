package org.around.ieuri.auth.domain.member
import org.around.ieuri.auth.domain.member.Agreement
import org.around.ieuri.auth.domain.member.Email
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.Nickname
import org.around.ieuri.auth.domain.member.PasswordHash
import org.around.ieuri.auth.domain.member.Profile
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderUserId
import java.time.Instant

/**
 * 회원 애그리거트 루트. 가입 불변식(필수 약관 동의 / 만 14세 이상 / 이메일 필수)과 정지/복구 전이를 강제한다.
 * - 탈퇴는 상태 전이가 아니라 행 삭제(hard delete)라 도메인 상태로 남기지 않는다.
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

    /** 이메일 변경 가능 여부(SPEC-0024 R6). `Provider.EMAIL` 회원만 참이다. */
    fun canChangeEmail(): Boolean = provider == Provider.EMAIL

    /**
     * 인증 대기 주소를 정정할 수 있는 회원인지 확인한다(SPEC-0024 R7). 인증을 마쳤으면 거부한다.
     * - 주소를 여기서 바꾸지 않는다. 정정된 주소는 소유를 증명할 때까지 검증 챌린지가 들고 있다.
     */
    fun ensureCanRetargetPendingEmail() {
        require(!emailVerified) { "이미 이메일 인증을 마쳐 대상 주소를 정정할 수 없습니다." }
    }

    /** 닉네임 변경(SPEC-0011 R5). 길이·공백 규칙은 [Nickname]이 판정한다. */
    fun changeNickname(nickname: Nickname) {
        profile.changeNickname(nickname)
    }

    /** 프로필 이미지 교체(R6). 확정된 오브젝트 키만 들어온다. */
    fun changeProfileImage(objectKey: String) {
        profile.changeProfileImage(objectKey)
    }

    /** 프로필 이미지 제거(R6). 이미 없어도 오류가 아니다(멱등). */
    fun removeProfileImage() {
        profile.changeProfileImage(null)
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
    fun isSuspensionExpired(now: Instant): Boolean = status == MemberStatus.SUSPENDED && suspendedUntil != null && !now.isBefore(suspendedUntil)

    companion object {
        /**
         * SNS 가입(SPEC-0024 R2·R3·R12). 인증 면제는 `providerEmail != null` 하나로 판정한다.
         * @param providerEmail provider 응답의 이메일. 있으면 이 값이 저장되고 인증이 면제된다.
         * @param fallbackEmail provider가 이메일을 주지 않았을 때 쓸 사용자 입력값(R3).
         */
        fun register(
            provider: Provider,
            providerUserId: ProviderUserId,
            providerEmail: Email?,
            fallbackEmail: Email,
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

            return Member(
                id = null,
                provider = provider,
                providerUserId = providerUserId,
                email = providerEmail ?: fallbackEmail,
                emailVerifiedInitial = providerEmail != null,
                statusInitial = MemberStatus.ACTIVE,
                ageVerified = ageVerified,
                suspendedAtInitial = null,
                suspendedUntilInitial = null,
                suspensionReasonInitial = null,
                profile = Profile(nicknameInitial = nickname, profileImageUrlInitial = profileImageUrl),
                agreements = agreements,
                passwordHash = null,
                createdAt = now,
            )
        }

        /**
         * 자체(EMAIL) 회원가입. 미인증(emailVerified=false)으로 시작하며, 약관·만 14세 불변식은 SNS 가입과 같다.
         * - providerUserId는 호출자가 정규화된 이메일을 보호 변환해 넘긴 값이다(SPEC-0012 R1).
         */
        fun registerByEmail(
            email: Email,
            providerUserId: ProviderUserId,
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
                providerUserId = providerUserId,
                email = email,
                emailVerifiedInitial = false,
                statusInitial = MemberStatus.ACTIVE,
                ageVerified = ageVerified,
                suspendedAtInitial = null,
                suspendedUntilInitial = null,
                suspensionReasonInitial = null,
                profile = Profile(nicknameInitial = nickname, profileImageUrlInitial = null),
                agreements = agreements,
                passwordHash = passwordHash,
                createdAt = now,
            )
        }

        /** 영속 상태에서 도메인 모델 재구성(매퍼/상태 변경 전용). 동의 목록은 재적재하지 않는다(빈 목록). */
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
            profile = Profile(nicknameInitial = nickname, profileImageUrlInitial = profileImageUrl),
            agreements = emptyList(),
            passwordHash = null,
            createdAt = createdAt,
        )
    }
}
