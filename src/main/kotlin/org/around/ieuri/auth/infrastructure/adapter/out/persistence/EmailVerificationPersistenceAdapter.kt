package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.auth.application.port.out.persistence.EmailVerificationRepositoryPort
import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.domain.vo.VerificationPurpose
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.QEmailVerificationEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toNewEntity
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
    private val queryFactory: JPAQueryFactory,
) : EmailVerificationRepositoryPort {

    private val verification = QEmailVerificationEntity.emailVerificationEntity

    override fun save(verification: EmailVerification) {
        repository.save(verification.toNewEntity())
    }

    override fun findByPublicId(publicId: UUID): EmailVerification? =
        repository.findByPublicId(publicId)?.toDomain()

    override fun update(verification: EmailVerification) {
        val entity = repository.findByPublicId(verification.publicId) ?: return
        entity.attempts = verification.attempts
        entity.consumed = verification.consumed
        entity.verifiedAt = verification.verifiedAt
        // dirty-check로 UPDATE.
    }

    override fun findLatestCreatedAtByMemberIdAndPurpose(
        memberId: Long,
        purpose: VerificationPurpose,
    ): Instant? = repository.findFirstByMemberIdAndPurposeOrderByCreatedAtDesc(memberId, purpose)?.createdAt

    /** 같은 용도의 활성 챌린지만 소비 처리한다 — 다른 용도의 진행 중인 검증은 건드리지 않는다. */
    override fun consumeActiveByMemberIdAndPurpose(
        memberId: Long,
        purpose: VerificationPurpose,
        at: Instant,
    ) {
        queryFactory
            .update(verification)
            .set(verification.consumed, true)
            .where(
                verification.memberId.eq(memberId),
                verification.purpose.eq(purpose),
                verification.consumed.isFalse,
                verification.verifiedAt.isNull,
            )
            .execute()
    }
}
