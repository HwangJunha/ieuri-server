package org.around.ieuri.auth.application.member.provided.dto

/** 비밀번호 재설정 실행 커맨드. 이메일+코드로 검증하고 새 비밀번호로 교체한다. */
data class ResetPasswordCommand(
    val email: String,
    val code: String,
    val newPassword: String,
) {
    init {
        require(email.isNotBlank()) { "이메일은 필수입니다." }
        require(code.matches(Regex("^\\d{6}$"))) { "코드는 6자리 숫자여야 합니다." }
        validatePasswordPolicy(newPassword)
    }
}
