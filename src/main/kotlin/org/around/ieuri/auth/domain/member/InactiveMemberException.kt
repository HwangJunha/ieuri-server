package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.AuthUserException

/** 정지된 회원이 로그인하려 할 때. 메시지에 정지 사유가 붙는다. */
class InactiveMemberException(message: String) : AuthUserException("ACCOUNT_INACTIVE", message)
