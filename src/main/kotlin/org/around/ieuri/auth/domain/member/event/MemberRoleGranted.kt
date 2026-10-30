package org.around.ieuri.auth.domain.member.event

/** 회원에게 역할이 부여됐다. */
data class MemberRoleGranted(
    val memberId: Long,
    val roleCode: String,
    val actorMemberId: Long,
)
