package org.around.ieuri.auth.application.member.provided.dto

/** 인증 주소 재입력 커맨드(SPEC-0024 R7). 검증 핸들이 아니라 인증된 [memberId]로 대상을 정한다. */
data class RetargetEmailVerificationCommand(
    val memberId: Long,
    val newEmail: String,
)
