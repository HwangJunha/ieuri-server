package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.AuthUserException

/** 로그인 시 (provider, provider_user_id)에 해당하는 회원이 없을 때 → 회원가입 유도. */
class MemberNotFoundException(message: String = "회원을 찾을 수 없습니다.") : AuthUserException("MEMBER_NOT_FOUND", message)
