package org.around.ieuri.auth.application.member.provided.dto
import org.around.ieuri.auth.application.member.provided.exception.PasswordPolicyViolationException

/** 비밀번호 정책 검증: 8~100자, 영문·숫자·특수문자 모두 포함. 위반 시 INVALID_INPUT이 아니라 [PasswordPolicyViolationException]. */
fun validatePasswordPolicy(password: String) {
    fun requirePolicy(satisfied: Boolean, message: String) {
        if (!satisfied) throw PasswordPolicyViolationException(message)
    }
    requirePolicy(password.length in 8..100, "비밀번호는 8자 이상 100자 이하여야 합니다.")
    requirePolicy(password.any { it.isLetter() }, "비밀번호에 영문자를 포함해야 합니다.")
    requirePolicy(password.any { it.isDigit() }, "비밀번호에 숫자를 포함해야 합니다.")
    requirePolicy(
        password.any { !it.isLetterOrDigit() && !it.isWhitespace() },
        "비밀번호에 특수문자를 포함해야 합니다.",
    )
}
