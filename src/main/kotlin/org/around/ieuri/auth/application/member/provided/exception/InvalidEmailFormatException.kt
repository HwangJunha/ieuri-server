package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 이메일 형식이 올바르지 않을 때(R7). */
class InvalidEmailFormatException(message: String) : UserFacingException("INVALID_EMAIL_FORMAT", message)
