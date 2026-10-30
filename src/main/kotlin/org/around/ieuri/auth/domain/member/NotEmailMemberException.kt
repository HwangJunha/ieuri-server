package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.AuthUserException

/** SNS 회원이 자체(EMAIL) 회원 전용 기능을 쓰려 할 때(SPEC-0024 R6). */
class NotEmailMemberException(message: String) : AuthUserException("NOT_EMAIL_MEMBER", message)
