package org.around.ieuri.auth.domain.member

/** 코드 검증 실패 사유. 이름이 그대로 응답의 `failureReason`이다. */
enum class VerificationFailureReason {
    EXPIRED,
    CONSUMED,
    MAX_ATTEMPTS_EXCEEDED,
    CODE_MISMATCH,
}
