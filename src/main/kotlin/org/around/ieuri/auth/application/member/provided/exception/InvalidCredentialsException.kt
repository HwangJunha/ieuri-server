package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 자체(EMAIL) 로그인 실패(이메일 없음/비밀번호 불일치/비밀번호 미설정 계정). 계정 존재 여부를 드러내지 않는다. */
class InvalidCredentialsException(message: String) : UserFacingException("INVALID_CREDENTIALS", message)
