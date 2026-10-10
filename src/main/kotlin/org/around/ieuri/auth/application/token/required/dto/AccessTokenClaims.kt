package org.around.ieuri.auth.application.token.required.dto

/** 검증된 access 토큰의 클레임. permissions로 인가를 판정하고, roles는 표시/감사용이다. */
data class AccessTokenClaims(
    val memberId: Long,
    val permissions: Set<String>,
    val roles: Set<String>,
)
