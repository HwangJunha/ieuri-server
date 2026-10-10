package org.around.ieuri.auth.domain.member

/** 재설정 코드 확인 한 번의 결과. 성패와 무관하게 [reset]은 저장해야 한다(시도 횟수·소진). */
data class PasswordResetAttempt(
    val reset: PasswordReset,
    val result: VerificationResult,
)
