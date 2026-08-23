package org.around.ieuri.auth.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.auth.domain.aggregate.EmailVerification
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.EmailVerificationEntity

/** 이메일 검증 챌린지 엔티티를 도메인 모델로 옮긴다. */
fun EmailVerificationEntity.toDomain(): EmailVerification =
    EmailVerification.reconstitute(
        id = id,
        publicId = publicId,
        memberId = memberId,
        codeHash = codeHash,
        expiresAt = expiresAt,
        attempts = attempts,
        maxAttempts = maxAttempts,
        consumed = consumed,
        verifiedAt = verifiedAt,
        createdAt = requireNotNull(createdAt),
        purpose = purpose,
        targetEmailEncrypted = targetEmailEncrypted,
    )

/** 도메인 모델을 새 엔티티로 옮긴다. id는 DB가 채운다. */
fun EmailVerification.toNewEntity(): EmailVerificationEntity =
    EmailVerificationEntity(
        publicId = publicId,
        memberId = memberId,
        codeHash = codeHash,
        expiresAt = expiresAt,
        attempts = attempts,
        maxAttempts = maxAttempts,
        consumed = consumed,
        verifiedAt = verifiedAt,
        purpose = purpose,
        targetEmailEncrypted = targetEmailEncrypted,
    )
