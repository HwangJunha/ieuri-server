package org.around.ieuri.auth.application.member

/**
 * 비밀번호 재설정 코드가 발급됐다. 평문 코드는 이 이벤트로만 전달된다.
 * - 커밋 뒤 리스너가 메일을 보내므로 트랜잭션이 롤백되면 발송되지 않는다.
 */
data class PasswordResetRequested(
    val memberId: Long,
    val toEmail: String,
    val code: String,
)
