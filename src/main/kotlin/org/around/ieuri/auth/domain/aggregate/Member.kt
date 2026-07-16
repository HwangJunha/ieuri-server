package org.around.ieuri.auth.domain.aggregate

import org.around.ieuri.auth.domain.entity.Profile
import org.around.ieuri.auth.domain.vo.Agreement
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Nickname
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.ProviderUserId
import java.time.Instant

/**
 * 회원 애그리거트 루트. 가입 불변식(필수 약관 동의 / 만 14세 이상 / 이메일 필수)은
 * 오직 이곳에서 강제된다. Profile과 동의 이력(Agreement)을 소유한다.
 *
 * 이메일 검증 여부는 [register] 시점에 provider 검증 이메일과 제출 이메일을 비교해 판정한다.
 * (수정했거나 provider 미검증이면 미검증 상태로 시작하고, 이후 코드 검증으로 완료)
 */
class Member private constructor(
    val id: Long?,
    val provider: Provider,
    val providerUserId: ProviderUserId,
    val email: Email,
    emailVerifiedInitial: Boolean,
    val status: MemberStatus,
    val ageVerified: Boolean,
    val profile: Profile,
    val agreements: List<Agreement>,
    val createdAt: Instant,
) {
    var emailVerified: Boolean = emailVerifiedInitial
        private set

    /** 이메일 검증 완료 시 호출. 코드 검증 성공(EmailVerification.verify)의 결과로만 전이된다. */
    fun markEmailVerified() {
        emailVerified = true
    }

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
                status = MemberStatus.ACTIVE,
                ageVerified = ageVerified,
                profile = Profile(nickname = nickname, profileImageUrl = profileImageUrl),
                agreements = agreements,
                createdAt = now,
            )
        }
    }
}
