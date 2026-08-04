package org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.auth.domain.aggregate.PasswordReset
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.PasswordResetEntity

/** 비밀번호 재설정 챌린지 엔티티를 도메인 모델로 옮긴다. */
fun PasswordResetEntity.toDomain(): PasswordReset =
    PasswordReset.reconstitute(
        id = id,
        publicId = publicId,
        memberId = memberId,
        codeHash = codeHash,
        expiresAt = expiresAt,
        attempts = attempts,
        maxAttempts = maxAttempts,
        consumed = consumed,
        createdAt = requireNotNull(createdAt),
    )

/** 도메인 모델을 새 엔티티로 옮긴다. id는 DB가 채운다. */
fun PasswordReset.toNewEntity(): PasswordResetEntity =
    PasswordResetEntity(
        publicId = publicId,
        memberId = memberId,
        codeHash = codeHash,
        expiresAt = expiresAt,
        attempts = attempts,
        maxAttempts = maxAttempts,
        consumed = consumed,
    )
