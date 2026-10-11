package org.around.ieuri.auth.application.member.provided.dto
import java.util.UUID

/** 이메일 변경 코드 재발송(R7). 쿨다운은 이메일 변경 용도로만 센다. */
data class ResendEmailChangeCommand(
    val verificationPublicId: UUID,
)
