package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 다른 회원이 이미 쓰는 주소로 이메일을 바꾸려 할 때(SPEC-0011 R7). 상대 정보는 담지 않는다(POL-013). */
class EmailAlreadyInUseException(message: String) : UserFacingException("EMAIL_ALREADY_IN_USE", message)
