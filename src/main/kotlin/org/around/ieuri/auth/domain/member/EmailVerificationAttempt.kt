package org.around.ieuri.auth.domain.member

/** 인증 코드 확인 한 번의 결과. 성패와 무관하게 [verification]은 저장해야 한다(시도 횟수·소진). */
data class EmailVerificationAttempt(
    val member: Member,
    val verification: EmailVerification,
    val result: VerificationResult,
)
