package org.around.ieuri.auth.domain.member.event

/** 회원이 재설정 코드로 비밀번호를 바꿨다. */
data class PasswordResetCompleted(
    val memberId: Long,
)
