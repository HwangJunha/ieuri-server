package org.around.ieuri.auth.application.member.provided.dto
/** 역할 회수 커맨드. actor==target이고 마지막 관리 역할이면 거부한다(자기 강등 방지). */
data class RevokeRoleCommand(
    val actorMemberId: Long,
    val targetMemberId: Long,
    val roleCode: String,
)
