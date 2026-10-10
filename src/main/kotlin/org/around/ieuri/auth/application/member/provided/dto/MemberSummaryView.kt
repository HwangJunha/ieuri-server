package org.around.ieuri.auth.application.member.provided.dto
import org.around.ieuri.auth.domain.member.MemberStatus
import java.time.Instant

/** 관리자 회원 목록 항목. 이메일은 포함하지 않는다(대량 복호화 회피). */
data class MemberSummaryView(
    val memberId: Long,
    val nickname: String,
    val status: MemberStatus,
    val roles: List<String>,
    val createdAt: Instant,
)
