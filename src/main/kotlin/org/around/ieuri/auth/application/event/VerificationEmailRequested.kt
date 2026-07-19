package org.around.ieuri.auth.application.event

/**
 * 검증 코드 메일 발송 요청 애플리케이션 이벤트.
 * 평문 코드는 저장하지 않고 이 이벤트로만(메모리) 전달되어 AFTER_COMMIT 리스너가 발송한다.
 * 트랜잭션이 롤백되면 발송되지 않는다.
 */
data class VerificationEmailRequested(
    val toEmail: String,
    val code: String,
)
