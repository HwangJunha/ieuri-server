package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 현재 자신의 이메일과 같은 주소를 제출할 때(R7). 보낼 코드가 없으므로 요청 자체를 거절한다. */
class SameAsCurrentEmailException(message: String) : UserFacingException("SAME_AS_CURRENT_EMAIL", message)
