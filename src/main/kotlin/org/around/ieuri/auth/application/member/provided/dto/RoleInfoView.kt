package org.around.ieuri.auth.application.member.provided.dto
/** 부여 가능한 역할(읽기 DTO). code는 assignRole/revokeRole의 roleCode로 그대로 쓰인다. */
data class RoleInfoView(
    val code: String,
    val name: String,
    val description: String?,
)
