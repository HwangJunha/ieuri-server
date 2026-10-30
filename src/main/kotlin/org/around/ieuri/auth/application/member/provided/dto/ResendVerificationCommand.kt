package org.around.ieuri.auth.application.member.provided.dto
import java.util.UUID

/** 검증 코드 재발송 커맨드. */
data class ResendVerificationCommand(
    val verificationPublicId: UUID,
)
