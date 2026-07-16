package org.around.ieuri.auth.infrastructure.adapter.out.persistence.adapter

import org.around.ieuri.auth.application.port.out.persistence.EmailVerificationRepositoryPort
import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.EmailVerificationEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.EmailVerificationJpaRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * 이메일 검증 챌린지 쓰기 포트 구현.
 * update()는 managed 엔티티를 로드해 attempts/consumed/verifiedAt를 반영하고 dirty-check에 맡긴다.
 */
@Component
class EmailVerificationPersistenceAdapter(
    private val repository: EmailVerificationJpaRepository,
) : EmailVerificationRepositoryPort {

    override fun save(verification: EmailVerification) {
        repository.save(
            EmailVerificationEntity(
                publicId = verification.publicId,
                memberId = verification.memberId,
                codeHash = verification.codeHash,
                expiresAt = verification.expiresAt,
                attempts = verification.attempts,
                maxAttempts = verification.maxAttempts,
                consumed = verification.consumed,
                verifiedAt = verification.verifiedAt,
            ),
        )
    }

    override fun findByPublicId(publicId: UUID): EmailVerification? =
        repository.findByPublicId(publicId)?.let { toDomain(it) }

    override fun update(verification: EmailVerification) {
        val entity = repository.findByPublicId(verification.publicId) ?: return
        entity.attempts = verification.attempts
        entity.consumed = verification.consumed
        entity.verifiedAt = verification.verifiedAt
        // dirty-check로 UPDATE.
    }

    override fun consumeActiveByMemberId(memberId: Long, at: Instant) {
        repository.consumeActiveByMemberId(memberId)
    }

    override fun findLatestCreatedAtByMemberId(memberId: Long): Instant? =
        repository.findLatestCreatedAtByMemberId(memberId)

    private fun toDomain(e: EmailVerificationEntity): EmailVerification =
        EmailVerification.reconstitute(
            id = e.id,
            publicId = e.publicId,
            memberId = e.memberId,
            codeHash = e.codeHash,
            expiresAt = e.expiresAt,
            attempts = e.attempts,
            maxAttempts = e.maxAttempts,
            consumed = e.consumed,
            verifiedAt = e.verifiedAt,
            createdAt = requireNotNull(e.createdAt),
        )
}
