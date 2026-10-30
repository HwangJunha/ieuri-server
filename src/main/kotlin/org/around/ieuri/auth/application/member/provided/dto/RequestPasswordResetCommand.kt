package org.around.ieuri.auth.application.member.provided.dto

/** 비밀번호 재설정 요청 커맨드(이메일로 코드 발송). */
data class RequestPasswordResetCommand(
    val email: String,
) {
    init {
        require(email.isNotBlank()) { "이메일은 필수입니다." }
    }
}
