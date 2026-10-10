package org.around.ieuri.auth.application.member.provided.dto
/** 프로필 이미지 제거(R6). 이미 없어도 성공한다(멱등). */
data class RemoveProfileImageCommand(
    val memberId: Long,
)
