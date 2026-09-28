package org.around.ieuri.auth.application.event

/**
 * 검증 코드 메일 발송 요청 이벤트. 평문 코드는 이 이벤트로만 전달된다.
 * - AFTER_COMMIT 리스너가 발송하므로 트랜잭션이 롤백되면 발송되지 않는다.
 */
data class VerificationEmailRequested(
    val toEmail: String,
    val code: String,
)
