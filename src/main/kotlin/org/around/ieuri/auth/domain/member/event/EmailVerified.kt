package org.around.ieuri.auth.domain.member.event
import java.time.Instant

/** 이메일 검증 완료 도메인 이벤트. */
data class EmailVerified(
    val memberId: Long,
    val occurredAt: Instant,
)
