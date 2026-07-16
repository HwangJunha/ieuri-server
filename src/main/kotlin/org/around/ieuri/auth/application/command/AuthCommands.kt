package org.around.ieuri.auth.application.command

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
) {
    init {
        require(idToken.isNotBlank()) { "idToken은 필수입니다." }
        require(submittedEmail.isNotBlank()) { "이메일은 필수 입력값입니다." }
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
