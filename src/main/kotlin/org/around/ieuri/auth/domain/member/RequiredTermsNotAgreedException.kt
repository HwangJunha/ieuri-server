package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.AuthUserException

/** 필수 약관에 동의하지 않고 가입하려 할 때. 메시지에 미동의 코드가 들어간다. */
class RequiredTermsNotAgreedException(message: String) : AuthUserException("INVALID_INPUT", message)
