package org.around.ieuri.auth.application.member.provided.dto

/** 이메일 변경 요청(SPEC-0011 R7). 새 주소로 6자리 코드를 보낸다. */
data class ChangeEmailCommand(
    val memberId: Long,
    val newEmail: String,
    /** 접속지 정보(접속기록용). 인바운드 어댑터가 헤더에서 읽어 넘기며, 판정 불가 시 null. */
    val ipAddress: String? = null,
)
