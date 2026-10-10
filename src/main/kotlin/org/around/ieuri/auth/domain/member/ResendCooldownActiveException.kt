package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.UserFacingException

/** 코드를 쿨다운 안에 다시 요청할 때(SPEC-0011 R7). VERIFICATION_RATE_LIMITED와 다른 코드다. */
class ResendCooldownActiveException(message: String = "잠시 후 다시 시도해 주세요.") : UserFacingException("RESEND_COOLDOWN_ACTIVE", message)
