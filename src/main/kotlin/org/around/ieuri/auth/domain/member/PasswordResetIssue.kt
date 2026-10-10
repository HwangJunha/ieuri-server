package org.around.ieuri.auth.domain.member

/** 재설정 코드 발급 결정: 폐기할 이전 코드들과 새 코드. */
data class PasswordResetIssue(
    val consumed: List<PasswordReset>,
    val issued: PasswordReset,
)
