package org.around.ieuri.auth.application.token.provided.dto

/** 자체(EMAIL) 로그인 커맨드. 이메일+비밀번호로 인증한다. */
data class LoginEmailCommand(
    val email: String,
    val password: String,
) {
    init {
        require(email.isNotBlank()) { "이메일은 필수입니다." }
        require(password.isNotBlank()) { "비밀번호는 필수입니다." }
    }
}
