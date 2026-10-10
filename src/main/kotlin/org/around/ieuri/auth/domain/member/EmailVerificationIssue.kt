package org.around.ieuri.auth.domain.member

/** 인증 코드 발급 결정: 폐기할 이전 코드들과 새 코드. */
data class EmailVerificationIssue(
    val consumed: List<EmailVerification>,
    val issued: EmailVerification,
)
