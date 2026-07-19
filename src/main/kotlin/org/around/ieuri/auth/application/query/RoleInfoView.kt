package org.around.ieuri.auth.application.query

/**
 * 부여 가능한 역할 정보(읽기 DTO). assignableRoles 조회로 프런트가 역할 드롭다운을 렌더한다.
 * code는 assignRole/revokeRole의 roleCode로 그대로 사용된다(데이터주도 — 코드 변경 없이 시드로 확장).
 */
data class RoleInfoView(
    val code: String,
    val name: String,
    val description: String?,
)
