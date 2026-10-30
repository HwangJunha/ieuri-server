package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.AuthUserException

/** 현재 이메일과 같은 주소로 바꾸려 할 때(SPEC-0011 R7). */
class SameAsCurrentEmailException(message: String = "현재 사용 중인 이메일과 같습니다.") : AuthUserException("SAME_AS_CURRENT_EMAIL", message)
