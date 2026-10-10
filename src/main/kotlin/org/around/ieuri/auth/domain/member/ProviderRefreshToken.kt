package org.around.ieuri.auth.domain.member

/** SNS provider가 준 refresh token. 탈퇴 때 연결을 끊는 데만 쓴다(SPEC-0012 R5). */
data class ProviderRefreshToken(
    val memberId: Long,
    val value: String,
)
