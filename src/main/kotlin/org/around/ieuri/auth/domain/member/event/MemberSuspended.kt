package org.around.ieuri.auth.domain.member.event
import java.time.Instant

/** 회원이 정지됐다. */
data class MemberSuspended(
    val memberId: Long,
    val reason: String,
    val suspendedUntil: Instant?,
    val occurredAt: Instant,
)
