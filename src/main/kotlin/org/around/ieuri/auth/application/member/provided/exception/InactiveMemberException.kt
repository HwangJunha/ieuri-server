package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** ACTIVE가 아닌 회원(정지/탈퇴 등)이 로그인/재발급을 시도할 때. */
class InactiveMemberException(message: String) : UserFacingException("ACCOUNT_INACTIVE", message)
