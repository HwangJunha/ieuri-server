package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.AuthUserException

/** 이메일 형식이 올바르지 않을 때(R7). */
class InvalidEmailFormatException(message: String = "이메일 형식이 올바르지 않습니다.") : AuthUserException("INVALID_EMAIL_FORMAT", message)
