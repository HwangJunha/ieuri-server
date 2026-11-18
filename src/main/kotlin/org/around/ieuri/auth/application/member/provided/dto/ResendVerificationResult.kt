package org.around.ieuri.auth.application.member.provided.dto
import java.time.Instant
import java.util.UUID

data class ResendVerificationResult(
    val verificationPublicId: UUID,
    val expiresAt: Instant,
)
