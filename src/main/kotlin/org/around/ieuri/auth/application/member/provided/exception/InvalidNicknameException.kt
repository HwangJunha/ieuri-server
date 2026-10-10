package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 닉네임 길이·형식이 규칙을 벗어날 때(R5). */
class InvalidNicknameException(message: String) : UserFacingException("INVALID_NICKNAME", message)
