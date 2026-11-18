package org.around.ieuri.auth.domain.member

/** 코드 검증 결과. 실패도 정상 응답이라 예외가 아니다. */
sealed interface VerificationResult {
    data object Success : VerificationResult

    data class Failure(val reason: VerificationFailureReason) : VerificationResult
}
