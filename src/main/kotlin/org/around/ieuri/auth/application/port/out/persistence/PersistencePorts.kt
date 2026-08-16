package org.around.ieuri.auth.application.port.out.persistence

import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.aggregate.PasswordReset
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.ProviderUserId
import org.around.ieuri.auth.domain.vo.VerificationPurpose
import java.time.Instant
import java.util.UUID

/**
 * 회원 쓰기 포트. 도메인 용어로만 표현되며 JPA를 노출하지 않는다.
 * 이메일 암호화/복호화는 이 포트의 구현(persistence adapter)이 책임진다.
 */
interface MemberRepositoryPort {
    fun existsByProviderAndProviderUserId(provider: Provider, providerUserId: ProviderUserId): Boolean

    /**
     * `(provider, email)` 중복 여부(SPEC-0024 R5). 다른 provider에 같은 주소가 있는 것은
     * 충돌이 아니다 — POL-002가 다계정을 허용해 `(KAKAO, a@b.com)`과 `(EMAIL, a@b.com)`은 공존한다.
     *
     * 인자는 평문이 아니라 **정규화된 이메일의 보호 식별자**다
     * ([org.around.ieuri.auth.application.port.out.security.LoginIdentifierProtectorPort.protect] 결과).
     * 저장된 이메일 암호문은 IV가 매번 달라 등호 조회가 불가능하기 때문이다.
     *
     * @param excludingMemberId 본인을 제외하고 판정할 회원 id. 가입처럼 본인이 없으면 null.
     */
    fun existsByProviderAndEmailIdentifier(
        provider: Provider,
        emailIdentifier: String,
        excludingMemberId: Long? = null,
    ): Boolean

    /** 회원 존재 여부(역할 부여/회수 대상 검증용). */
    fun existsById(memberId: Long): Boolean

    /** 회원 애그리거트(프로필 + 동의 이력)를 신규 저장하고 생성된 id를 반환한다. */
    fun save(member: Member): Long

    /** managed 엔티티를 로드해 email_verified를 갱신(dirty-check). */
    fun markEmailVerified(memberId: Long, verifiedAt: Instant)

    /** managed 엔티티를 로드해 password_hash를 갱신(dirty-check). 비밀번호 재설정에 사용. */
    fun updatePassword(memberId: Long, newPasswordHash: String)

    /** 검증메일 재발송 등을 위해 저장된 이메일을 복호화해 반환한다. */
    fun findEmailByMemberId(memberId: Long): Email?

    /**
     * 이메일 교체(SPEC-0011 R7·R8). managed 엔티티에 반영한다(dirty-check).
     *
     * **[protectedIdentifier]를 함께 받는 이유**: `Provider.EMAIL` 회원은 provider_user_id가
     * 곧 로그인 식별자(이메일의 HMAC)라 이메일만 바꾸면 다음 로그인이 실패한다. 두 값이
     * 반드시 같은 트랜잭션에서 함께 바뀌어야 하므로 한 메서드로 묶는다.
     * SNS 회원은 provider가 준 불투명 식별자를 쓰므로 null을 준다(건드리지 않는다).
     *
     * **[emailIdentifier]는 provider와 무관하게 항상 갱신된다**(SPEC-0024 R5) — 유니크 판정용
     * 파생 값이라 이메일과 어긋나면 제약이 엉뚱한 주소를 막는다. `provider_user_id`와 달리
     * SNS 회원도 대상이다.
     */
    fun updateEmail(memberId: Long, newEmail: Email, emailIdentifier: String, protectedIdentifier: String?)

    /** 로그인용 인증 정보 조회. (provider, provider_user_id)로 회원의 id·상태를 반환. 없으면 null. */
    fun findAuthentication(provider: Provider, providerUserId: ProviderUserId): MemberAuthentication?

    /**
     * 자체(EMAIL) 로그인용 인증 정보. 비밀번호 해시·검증 여부를 포함한다. 없으면 null.
     * 인자는 평문 이메일이 아니라 보호된 로그인 식별자다(LoginIdentifierProtectorPort.protect 결과).
     */
    fun findEmailAuthentication(protectedIdentifier: String): EmailAuthentication?

    /**
     * provider 폐기용 refresh token을 저장한다(암호화는 구현이 책임진다. SPEC-0012 R6).
     * 이미 값이 있으면 덮어쓴다 — 재로그인 시 provider가 새 토큰을 주기 때문이다.
     */
    fun updateProviderRefreshToken(memberId: Long, refreshToken: String)

    /** 저장된 provider refresh token을 복호화해 반환한다. 없으면 null. */
    fun findProviderRefreshToken(memberId: Long): String?

    /** 상태 변경(정지/복구)용 도메인 회원 로드. 동의 이력은 재적재하지 않는다. */
    fun load(memberId: Long): Member?

    /** 도메인의 상태·정지 메타를 managed 엔티티에 반영(dirty-check). */
    fun updateStatus(member: Member)

    /** 탈퇴: 회원과 소유 데이터(프로필·동의이력·이메일검증) 행을 삭제한다(감사 로그는 보존). */
    fun deleteMember(memberId: Long)
}

/** 로그인 인증에 필요한 최소 회원 정보. */
data class MemberAuthentication(
    val memberId: Long,
    val status: MemberStatus,
    val suspendedUntil: Instant?,
    val suspensionReason: String?,
)

/** 자체(EMAIL) 로그인 인증 정보. 비밀번호 해시와 이메일 검증 여부를 포함한다. */
data class EmailAuthentication(
    val memberId: Long,
    val status: MemberStatus,
    val suspendedUntil: Instant?,
    val suspensionReason: String?,
    val passwordHash: String?,
    val emailVerified: Boolean,
)

/** 활성 약관 조회 포트. 코드별 최신 유효 버전을 반환한다. */
interface TermsRepositoryPort {
    fun findActiveTerms(now: Instant): List<TermsView>
}

/** 약관 스냅샷(읽기용 뷰). 동의 이력 생성에 필요한 최소 정보만. */
data class TermsView(
    val id: Long,
    val code: String,
    val version: String,
    val required: Boolean,
)

/** 이메일 검증 챌린지 포트. */
interface EmailVerificationRepositoryPort {
    fun save(verification: EmailVerification): Unit

    fun findByPublicId(publicId: UUID): EmailVerification?

    /** attempts/consumed/verifiedAt 변경을 반영(dirty-check). */
    fun update(verification: EmailVerification)

    /**
     * 용도별 가장 최근 발급 시각. **쿨다운을 용도로 나눠 센다**(SPEC-0011 R7) —
     * 가입 검증과 이메일 변경이 한 창을 공유하면 한쪽을 보낸 직후 다른 쪽이 막힌다.
     * 가입 검증 안에서는 재발송과 주소 정정이 한 창을 공유한다(SPEC-0024 R7-3).
     */
    fun findLatestCreatedAtByMemberIdAndPurpose(memberId: Long, purpose: VerificationPurpose): Instant?

    /** 해당 용도의 활성 챌린지만 폐기한다. 다른 용도의 진행 중인 검증을 건드리지 않는다. */
    fun consumeActiveByMemberIdAndPurpose(memberId: Long, purpose: VerificationPurpose, at: Instant)
}

/** 감사 이력(outbox 겸용) 기록 포트. payload 직렬화는 구현이 책임진다. */
interface AuditLogPort {
    fun record(memberId: Long, eventType: String, payload: Map<String, Any?>)
}

/** 비밀번호 재설정 챌린지 포트. */
interface PasswordResetRepositoryPort {
    fun save(reset: PasswordReset)

    /** 회원의 활성(미소진·미만료 아님은 도메인 판정) 최신 챌린지. */
    fun findActiveByMemberId(memberId: Long): PasswordReset?

    /** attempts/consumed 변경을 반영(dirty-check). */
    fun update(reset: PasswordReset)

    /** 해당 회원의 활성 챌린지를 모두 폐기(새 요청 시). */
    fun consumeActiveByMemberId(memberId: Long)
}
