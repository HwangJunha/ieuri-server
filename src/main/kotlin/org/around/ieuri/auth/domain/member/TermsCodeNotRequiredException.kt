package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.AuthUserException

/** 선택 약관을 필수 동의 경로로 제출할 때(R12). */
class TermsCodeNotRequiredException(message: String = "필수 약관이 아닙니다.") : AuthUserException("TERMS_CODE_NOT_REQUIRED", message)
