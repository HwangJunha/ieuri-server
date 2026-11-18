package org.around.ieuri.auth.domain.member.event

import org.around.ieuri.auth.domain.member.Provider
import java.time.Instant
import java.util.UUID

/** 회원이 가입했다. 미인증이면 인증 핸들이 있다. */
data class MemberRegistered(
    val memberId: Long,
    val provider: Provider,
    val emailVerified: Boolean,
    val verificationPublicId: UUID?,
    val occurredAt: Instant,
)
