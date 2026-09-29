package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.auth.application.port.out.persistence.PasswordResetRepositoryPort
import org.around.ieuri.auth.domain.aggregate.PasswordReset
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.QPasswordResetEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.PasswordResetJpaRepository
import org.springframework.stereotype.Component
import java.time.Instant

/** 비밀번호 재설정 챌린지 쓰기 포트 구현. update()는 managed 엔티티를 고쳐 dirty-check에 맡긴다. */
@Component
class PasswordResetPersistenceAdapter(
    private val repository: PasswordResetJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : PasswordResetRepositoryPort {

    private val reset = QPasswordResetEntity.passwordResetEntity

    override fun save(reset: PasswordReset) {
        repository.save(reset.toNewEntity())
    }

    override fun findLatestCreatedAtByMemberId(memberId: Long): Instant? = repository.findFirstByMemberIdOrderByCreatedAtDesc(memberId)?.createdAt

    override fun findActiveByMemberId(memberId: Long): PasswordReset? = repository.findFirstByMemberIdAndConsumedFalseOrderByCreatedAtDesc(memberId)?.toDomain()

    override fun update(reset: PasswordReset) {
        val entity = repository.findByPublicId(reset.publicId) ?: return
        entity.attempts = reset.attempts
        entity.consumed = reset.consumed
        // dirty-check로 UPDATE.
    }

    /** 해당 회원의 미소비 재설정 챌린지를 한 번에 소비 처리한다. */
    override fun consumeActiveByMemberId(memberId: Long) {
        queryFactory
            .update(reset)
            .set(reset.consumed, true)
            .where(reset.memberId.eq(memberId), reset.consumed.isFalse)
            .execute()
    }
}
