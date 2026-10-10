package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 존재하지 않는 역할 코드로 부여/회수를 시도할 때. */
class RoleNotFoundException(message: String) : UserFacingException("ROLE_NOT_FOUND", message)
