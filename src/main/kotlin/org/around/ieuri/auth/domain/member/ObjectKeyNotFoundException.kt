package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.AuthUserException

/** 이 회원에게 발급된 임시 키가 아닐 때(SPEC-0011 R6). */
class ObjectKeyNotFoundException(message: String = "발급된 적 없는 오브젝트 키입니다.") : AuthUserException("OBJECT_KEY_NOT_FOUND", message)
