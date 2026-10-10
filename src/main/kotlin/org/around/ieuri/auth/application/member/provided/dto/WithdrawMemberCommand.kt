package org.around.ieuri.auth.application.member.provided.dto

/** 탈퇴 커맨드. memberId는 클라이언트 입력이 아니라 accessToken으로 인증된 본인 식별자다. */
data class WithdrawMemberCommand(
    val memberId: Long,
)
