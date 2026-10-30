package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.AuthUserException

/** 시행 중인 약관에 없는 코드일 때(SPEC-0011 R10·R12). */
class TermsNotFoundException(message: String = "시행 중인 약관이 아닙니다.") : AuthUserException("TERMS_NOT_FOUND", message)
