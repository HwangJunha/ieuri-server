package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 재발송 쿨다운(60초) 이내 재요청(R7). 이메일 변경 화면 전용 코드로, VERIFICATION_RATE_LIMITED와 다르다. */
class ResendCooldownActiveException(message: String) : UserFacingException("RESEND_COOLDOWN_ACTIVE", message)
