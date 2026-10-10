package org.around.ieuri.auth.domain.member.event
import org.around.ieuri.auth.domain.member.Provider
import java.time.Instant
import java.util.UUID

/** 회원가입 성공 이벤트. 미검증이면 verificationPublicId(검증 핸들)가 채워진다. */
data class MemberRegistered(
    val memberId: Long,
    val provider: Provider,
    val emailVerified: Boolean,
    val verificationPublicId: UUID?,
    val occurredAt: Instant,
)
