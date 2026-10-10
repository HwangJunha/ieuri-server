package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.UserFacingException

/** 닉네임 길이가 규칙을 벗어날 때(SPEC-0011 R5). */
class InvalidNicknameException(message: String) : UserFacingException("INVALID_NICKNAME", message)
