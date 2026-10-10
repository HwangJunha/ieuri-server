package org.around.ieuri.auth.application.member.provided.dto
/** 프로필 이미지 업로드 확인(R6). 발급받은 임시 키를 되돌려준다. */
data class ConfirmProfileImageUploadCommand(
    val memberId: Long,
    val objectKey: String,
)
