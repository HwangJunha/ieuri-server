package org.around.ieuri.auth.application.port.out.persistence

import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.domain.aggregate.Member
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.domain.vo.ProviderUserId
import java.time.Instant
import java.util.UUID

/**
 * 회원 쓰기 포트. 도메인 용어로만 표현되며 JPA를 노출하지 않는다.
 * 이메일 암호화/복호화는 이 포트의 구현(persistence adapter)이 책임진다.
 */
interface MemberRepositoryPort {
    fun existsByProviderAndProviderUserId(provider: Provider, providerUserId: ProviderUserId): Boolean

    /** 회원 애그리거트(프로필 + 동의 이력)를 신규 저장하고 생성된 id를 반환한다. */
    fun save(member: Member): Long

    /** managed 엔티티를 로드해 email_verified를 갱신(dirty-check). */
    fun markEmailVerified(memberId: Long, verifiedAt: Instant)

    /** 검증메일 재발송 등을 위해 저장된 이메일을 복호화해 반환한다. */
    fun findEmailByMemberId(memberId: Long): Email?
}

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

    /** 해당 회원의 활성(미소진·미검증) 챌린지를 모두 폐기(재발송 시). */
    fun consumeActiveByMemberId(memberId: Long, at: Instant)

    /** 재발송 rate limit 판정을 위한 가장 최근 발급 시각. */
    fun findLatestCreatedAtByMemberId(memberId: Long): Instant?
}

/** 감사 이력(outbox 겸용) 기록 포트. payload 직렬화는 구현이 책임진다. */
interface AuditLogPort {
    fun record(memberId: Long, eventType: String, payload: Map<String, Any?>)
}
