package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** SNS 회원이 이메일 변경을 요청할 때(SPEC-0024 R6). */
class NotEmailMemberException(message: String) : UserFacingException("NOT_EMAIL_MEMBER", message)
