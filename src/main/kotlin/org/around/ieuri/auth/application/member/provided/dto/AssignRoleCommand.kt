package org.around.ieuri.auth.application.member.provided.dto
/** 역할 부여 입력. target은 역할을 받는 회원이다. 수행자는 담지 않는다. */
data class AssignRoleCommand(
    val targetMemberId: Long,
    val roleCode: String,
)
