package org.around.ieuri.auth.application.member.provided.dto
import org.around.ieuri.auth.domain.member.MemberStatus
import java.time.Instant

/** 관리자 회원 단건 상세(member). member:read 권한 필요. 단건이므로 이메일 평문 노출. */
data class MemberDetailView(
    val memberId: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val status: MemberStatus,
    val emailVerified: Boolean,
    val suspendedAt: Instant?,
    val suspendedUntil: Instant?,
    val suspensionReason: String?,
    val roles: List<String>,
    val createdAt: Instant,
)
