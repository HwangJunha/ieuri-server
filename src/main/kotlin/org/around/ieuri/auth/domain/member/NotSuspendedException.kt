package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.AuthUserException

/** 정지 상태가 아닌 회원을 복구하려 할 때. */
class NotSuspendedException(message: String = "정지 상태에서만 복구할 수 있습니다.") : AuthUserException("INVALID_INPUT", message)
