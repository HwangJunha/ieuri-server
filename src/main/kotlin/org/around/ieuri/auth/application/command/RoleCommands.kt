package org.around.ieuri.auth.application.command

/**
 * 역할 부여 커맨드. actor는 요청을 수행하는(권한을 가진) 회원, target은 역할을 받는 회원.
 * actor는 감사 기록에 남긴다.
 */
data class AssignRoleCommand(
    val actorMemberId: Long,
    val targetMemberId: Long,
    val roleCode: String,
)

/**
 * 역할 회수 커맨드. actor==target(본인) 이고 마지막 관리 역할이면 거부한다(자기 강등 방지).
 */
data class RevokeRoleCommand(
    val actorMemberId: Long,
    val targetMemberId: Long,
    val roleCode: String,
)
