package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.AuthUserException

/** 정지 사유가 비었거나 만료일이 지난 시각일 때. */
class InvalidSuspensionException(message: String) : AuthUserException("INVALID_INPUT", message)
