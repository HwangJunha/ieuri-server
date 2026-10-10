package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.UserFacingException

/** 만 14세 확인 없이 가입하려 할 때. */
class UnderageRegistrationException(message: String = "만 14세 미만은 가입할 수 없습니다.") : UserFacingException("INVALID_INPUT", message)
