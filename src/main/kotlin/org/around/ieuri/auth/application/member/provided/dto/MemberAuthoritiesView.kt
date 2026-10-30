package org.around.ieuri.auth.application.member.provided.dto

/** 회원의 역할 코드와 유효 권한 코드. 토큰 발급이 쓴다. */
data class MemberAuthoritiesView(
    val roles: Set<String>,
    val permissions: Set<String>,
)
