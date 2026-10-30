package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.AuthUserException

/** 인증을 마친 회원이 인증 주소를 정정하려 할 때(SPEC-0024 R7-2). */
class AlreadyVerifiedException(message: String = "이미 이메일 인증을 마쳤습니다.") : AuthUserException("ALREADY_VERIFIED", message)
