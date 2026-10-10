package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 인증을 이미 마친 회원이 인증 주소 재입력을 요청할 때(SPEC-0024 R7-2). 클라이언트는 인증 화면을 벗어나야 한다. */
class AlreadyVerifiedException(message: String) : UserFacingException("ALREADY_VERIFIED", message)
