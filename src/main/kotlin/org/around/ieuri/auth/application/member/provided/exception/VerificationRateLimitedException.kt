package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 재발송 쿨다운 이내 재요청 등 rate limit 위반. */
class VerificationRateLimitedException(message: String) : UserFacingException("VERIFICATION_RATE_LIMITED", message)
