package org.around.ieuri.auth.application.command

import org.around.ieuri.auth.application.exception.PasswordPolicyViolationException
import org.around.ieuri.auth.domain.vo.Provider
import java.util.UUID

/**
 * 회원가입 커맨드(쓰기 입력). GraphQL input에서 변환된다.
 * 형식/shape 검증만 여기서 수행하고, 업무 불변식은 도메인(Member.register)에서 강제한다.
 */
data class RegisterMemberCommand(
    val provider: Provider,
    val idToken: String,
    /** 폼 제출 이메일(필수). provider 값이 기본 채워지되 사용자가 수정 가능. */
    val submittedEmail: String,
    /** 만 14세 이상 확인 체크값. */
    val ageVerified: Boolean,
    val agreements: List<AgreementInput>,
    /** 탈퇴 시 연결 해제용 토큰을 확보하기 위한 provider authorization code. 확보 실패해도 가입은 성공한다. */
    val authorizationCode: String? = null,
    /**
     * 클라이언트가 provider에서 받아 넘긴 닉네임(SPEC-0032 R29).
     *
     * **형식을 여기서 검사하지 않는다** — provider가 주는 값이라 클라이언트가 고칠 수 없고,
     * 규칙을 벗어나면 서버가 조용히 자동 생성으로 대체한다. `require`로 막으면 가입이
     * 실패하는데, 그것은 사용자가 손쓸 수 없는 이유로 막는 것이다.
     */
    val nickname: String? = null,
) {
    init {
        require(idToken.isNotBlank()) { "idToken은 필수입니다." }
        require(submittedEmail.isNotBlank()) { "이메일은 필수 입력값입니다." }
    }
}

/**
 * 자체(EMAIL) 회원가입 커맨드. 이메일+비밀번호로 가입하고 이메일 인증으로 활성화한다.
 * 비밀번호 정책(개인정보보호법 안전성 확보조치 기준: 3종 조합 8자 이상)은 여기서 형식 검증한다.
 */
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

/**
 * 비밀번호 정책 검증(형식). 영문·숫자·특수문자 3종을 모두 포함하고 8자 이상이어야 한다.
 * (개인정보보호법 「개인정보의 안전성 확보조치 기준」: 3종 조합 시 8자 이상 충족.)
 */
fun validatePasswordPolicy(password: String) {
    // 전용 코드로 던진다 — INVALID_INPUT으로 뭉뚱그리면 클라이언트가 어느 입력이 문제인지
    // 알 수 없고, 서버 message에 의존해야 해 다국어 대응이 불가능하다(POL-022).
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

/** 비밀번호 재설정 요청 커맨드(이메일로 코드 발송). 계정 유무는 응답으로 드러내지 않는다. */
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

/**
 * 인증 주소 재입력 커맨드(SPEC-0024 R7).
 *
 * 검증 핸들이 아니라 **[memberId]로 대상을 정한다** — 발급된 핸들을 잃은 회원도 주소를
 * 고칠 수 있어야 하고, 이 오퍼레이션은 인증된 호출자만 쓰기 때문이다.
 * 주소 형식은 도메인 `Email`이 판정한다.
 */
data class RetargetEmailVerificationCommand(
    val memberId: Long,
    val newEmail: String,
)
