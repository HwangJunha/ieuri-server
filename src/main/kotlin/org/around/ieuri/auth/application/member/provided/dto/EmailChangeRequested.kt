package org.around.ieuri.auth.application.member.provided.dto
import java.time.Instant
import java.util.UUID

/** 코드 발송 결과. 핸들과 만료만 돌려주고 코드는 메일로만 나간다. */
data class EmailChangeRequested(
    val verificationPublicId: UUID,
    val expiresAt: Instant,
)
