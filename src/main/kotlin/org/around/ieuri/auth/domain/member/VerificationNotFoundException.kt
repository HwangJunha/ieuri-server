package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.AuthUserException

/** 핸들에 해당하는 인증 코드가 없거나, 이 회원·이 용도의 코드가 아닐 때. */
class VerificationNotFoundException(message: String = "유효하지 않은 검증 요청입니다.") : AuthUserException("VERIFICATION_NOT_FOUND", message)
