package org.around.ieuri.auth.domain.member.event
import java.time.Instant

/** 관리자가 회원의 정지를 풀었다. */
data class MemberReactivated(
    val memberId: Long,
    val occurredAt: Instant,
)
