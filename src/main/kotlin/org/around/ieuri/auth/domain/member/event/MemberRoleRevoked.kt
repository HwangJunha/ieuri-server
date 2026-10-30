package org.around.ieuri.auth.domain.member.event

/** 회원에게서 역할이 회수됐다. */
data class MemberRoleRevoked(
    val memberId: Long,
    val roleCode: String,
    val actorMemberId: Long,
)
