package org.around.ieuri.auth.domain.member.event

import java.time.Instant
import java.util.UUID

/** 회원이 가입 이메일의 소유를 증명했다. */
data class EmailVerified(
    val memberId: Long,
    val verificationPublicId: UUID,
    val occurredAt: Instant,
)
