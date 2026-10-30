package org.around.ieuri.auth.application.member.provided.dto

/** 자체(EMAIL) 회원가입 커맨드. 비밀번호 정책은 여기서 형식 검증한다. */
data class RegisterEmailMemberCommand(
    val email: String,
    val password: String,
    val ageVerified: Boolean,
    val agreements: List<AgreementInput>,
) {
    init {
        require(email.isNotBlank()) { "이메일은 필수 입력값입니다." }
        validatePasswordPolicy(password)
    }
}
