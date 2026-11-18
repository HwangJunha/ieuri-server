package org.around.ieuri.auth.application.member.provided.dto
import java.time.Instant
import java.util.UUID

/** 인증 주소 재입력 결과. 새 주소로 재발급된 검증 핸들과 만료 시각. */
data class RetargetEmailVerificationResult(
    val verificationPublicId: UUID,
    val expiresAt: Instant,
)
