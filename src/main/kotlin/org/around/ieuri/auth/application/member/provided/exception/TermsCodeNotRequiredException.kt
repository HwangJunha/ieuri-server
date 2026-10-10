package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 선택 약관을 필수 동의 경로로 제출할 때(R12). */
class TermsCodeNotRequiredException(message: String) : UserFacingException("TERMS_CODE_NOT_REQUIRED", message)
