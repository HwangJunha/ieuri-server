package org.around.ieuri.auth.application.member.provided.dto
/** 역할 회수 입력. target은 역할을 잃는 회원이다. 수행자는 담지 않는다. */
data class RevokeRoleCommand(
    val targetMemberId: Long,
    val roleCode: String,
)
