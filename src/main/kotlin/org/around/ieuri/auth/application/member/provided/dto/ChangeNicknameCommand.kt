package org.around.ieuri.auth.application.member.provided.dto
/** 닉네임 변경(SPEC-0011 R5). 공백 제거·길이 판정은 Nickname 값 객체가 한다. */
data class ChangeNicknameCommand(
    val memberId: Long,
    val nickname: String,
)
