package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.UserFacingException

/** 가입 인증 코드를 쿨다운 안에 다시 요청할 때. */
class VerificationRateLimitedException(message: String = "잠시 후 다시 시도해 주세요.") : UserFacingException("VERIFICATION_RATE_LIMITED", message)
