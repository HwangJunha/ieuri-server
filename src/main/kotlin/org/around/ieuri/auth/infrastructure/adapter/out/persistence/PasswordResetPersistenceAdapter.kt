package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import org.around.ieuri.auth.application.port.out.persistence.PasswordResetRepositoryPort
import org.around.ieuri.auth.domain.aggregate.PasswordReset
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.PasswordResetEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.PasswordResetJpaRepository
import org.springframework.stereotype.Component

/**
 * 비밀번호 재설정 챌린지 쓰기 포트 구현.
 * update()는 managed 엔티티를 로드해 attempts/consumed를 반영하고 dirty-check에 맡긴다.
 */
@Component
class PasswordResetPersistenceAdapter(
    private val repository: PasswordResetJpaRepository,
) : PasswordResetRepositoryPort {

    override fun save(reset: PasswordReset) {
        repository.save(
            PasswordResetEntity(
                publicId = reset.publicId,
                memberId = reset.memberId,
                codeHash = reset.codeHash,
                expiresAt = reset.expiresAt,
                attempts = reset.attempts,
                maxAttempts = reset.maxAttempts,
                consumed = reset.consumed,
            ),
        )
    }

    override fun findActiveByMemberId(memberId: Long): PasswordReset? =
        repository.findFirstByMemberIdAndConsumedFalseOrderByCreatedAtDesc(memberId)?.let { toDomain(it) }

    override fun update(reset: PasswordReset) {
        val entity = repository.findByPublicId(reset.publicId) ?: return
        entity.attempts = reset.attempts
        entity.consumed = reset.consumed
        // dirty-check로 UPDATE.
    }

    override fun consumeActiveByMemberId(memberId: Long) {
        repository.consumeActiveByMemberId(memberId)
    }

    private fun toDomain(e: PasswordResetEntity): PasswordReset =
        PasswordReset.reconstitute(
            id = e.id,
            publicId = e.publicId,
            memberId = e.memberId,
            codeHash = e.codeHash,
            expiresAt = e.expiresAt,
            attempts = e.attempts,
            maxAttempts = e.maxAttempts,
            consumed = e.consumed,
            createdAt = requireNotNull(e.createdAt),
        )
}
