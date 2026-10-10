package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 이 회원에게 발급된 적 없는(또는 형식이 어긋난) 오브젝트 키일 때(R6). */
class ObjectKeyNotFoundException(message: String) : UserFacingException("OBJECT_KEY_NOT_FOUND", message)
