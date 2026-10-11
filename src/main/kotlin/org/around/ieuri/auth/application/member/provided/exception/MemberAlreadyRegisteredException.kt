package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.AuthUserException

/** 이미 같은 (provider, provider_user_id)로 가입된 회원이 있을 때. */
class MemberAlreadyRegisteredException(message: String = "이미 가입된 계정입니다.") : AuthUserException("MEMBER_ALREADY_REGISTERED", message)
