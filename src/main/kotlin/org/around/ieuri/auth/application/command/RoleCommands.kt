package org.around.ieuri.auth.application.command

/** 역할 부여 커맨드. actor는 수행 회원(감사 기록에 남는다), target은 역할을 받는 회원. */
data class AssignRoleCommand(
    val actorMemberId: Long,
    val targetMemberId: Long,
    val roleCode: String,
)

/** 역할 회수 커맨드. actor==target이고 마지막 관리 역할이면 거부한다(자기 강등 방지). */
data class RevokeRoleCommand(
    val actorMemberId: Long,
    val targetMemberId: Long,
    val roleCode: String,
)
