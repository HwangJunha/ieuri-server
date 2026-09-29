package org.around.ieuri.auth.application.command

import org.around.ieuri.auth.application.exception.PasswordPolicyViolationException
import org.around.ieuri.auth.domain.vo.Provider
import java.util.UUID

/** 회원가입 커맨드. 형식 검증만 하고 업무 불변식은 Member.register가 강제한다. */
data class RegisterMemberCommand(
    val provider: Provider,
    val idToken: String,
    /** 폼 제출 이메일(필수). */
    val submittedEmail: String,
    /** 만 14세 이상 확인 체크값. */
    val ageVerified: Boolean,
    val agreements: List<AgreementInput>,
    /** 탈퇴 시 연결 해제용 토큰을 확보하기 위한 provider authorization code. 확보 실패해도 가입은 성공한다. */
    val authorizationCode: String? = null,
    /** provider가 준 닉네임(SPEC-0032 R29). 형식을 검사하지 않는다 — 규칙을 벗어나면 서버가 자동 생성으로 대체한다. */
    val nickname: String? = null,
) {
    init {
        require(idToken.isNotBlank()) { "idToken은 필수입니다." }
        require(submittedEmail.isNotBlank()) { "이메일은 필수 입력값입니다." }
    }
}

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

/** 약관별 동의/비동의 입력. 비동의도 이력으로 남긴다. */
data class AgreementInput(
    val termsCode: String,
    val agreed: Boolean,
) {
    init {
        require(termsCode.isNotBlank()) { "termsCode는 비어 있을 수 없습니다." }
    }
}

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

/** 비밀번호 재설정 요청 커맨드(이메일로 코드 발송). */
data class RequestPasswordResetCommand(
    val email: String,
) {
    init {
        require(email.isNotBlank()) { "이메일은 필수입니다." }
    }
}

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

/** 이메일 검증 커맨드. 가입 응답으로 받은 검증 핸들(publicId) + 6자리 코드. */
data class VerifyEmailCommand(
    val verificationPublicId: UUID,
    val code: String,
) {
    init {
        require(code.matches(Regex("^\\d{6}$"))) { "코드는 6자리 숫자여야 합니다." }
    }
}

/** 검증 코드 재발송 커맨드. */
data class ResendVerificationCommand(
    val verificationPublicId: UUID,
)

/** 인증 주소 재입력 커맨드(SPEC-0024 R7). 검증 핸들이 아니라 인증된 [memberId]로 대상을 정한다. */
data class RetargetEmailVerificationCommand(
    val memberId: Long,
    val newEmail: String,
)
