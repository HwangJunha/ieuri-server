package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 시행 중인 약관에서 코드를 찾을 수 없을 때(R10·R12). */
class TermsNotFoundException(message: String) : UserFacingException("TERMS_NOT_FOUND", message)
