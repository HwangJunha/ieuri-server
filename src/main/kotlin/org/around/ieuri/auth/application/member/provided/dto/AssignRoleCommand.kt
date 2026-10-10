package org.around.ieuri.auth.application.member.provided.dto
/** 역할 부여 커맨드. actor는 수행 회원(감사 기록에 남는다), target은 역할을 받는 회원. */
data class AssignRoleCommand(
    val actorMemberId: Long,
    val targetMemberId: Long,
    val roleCode: String,
)
