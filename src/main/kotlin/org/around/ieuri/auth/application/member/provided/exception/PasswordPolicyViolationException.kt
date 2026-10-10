package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 비밀번호가 정책(8~100자·영문·숫자·특수문자)을 만족하지 않을 때. 어느 규칙을 어겼는지는 나누지 않는다. */
class PasswordPolicyViolationException(message: String) : UserFacingException("PASSWORD_POLICY_VIOLATION", message)
