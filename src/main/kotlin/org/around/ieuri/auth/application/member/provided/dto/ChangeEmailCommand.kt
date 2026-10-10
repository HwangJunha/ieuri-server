package org.around.ieuri.auth.application.member.provided.dto

/** 이메일 변경 요청(SPEC-0011 R7). 새 주소로 6자리 코드를 보낸다. */
data class ChangeEmailCommand(
    val memberId: Long,
    val newEmail: String,
)
