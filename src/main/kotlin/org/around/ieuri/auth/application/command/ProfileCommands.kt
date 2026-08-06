package org.around.ieuri.auth.application.command

/** 닉네임 변경(SPEC-0011 R5). 공백 제거·길이 판정은 Nickname 값 객체가 한다. */
data class ChangeNicknameCommand(
    val memberId: Long,
    val nickname: String,
)

/** 프로필 이미지 업로드 URL 발급(R6). 선언한 형식·크기가 서명에 포함된다. */
data class IssueProfileImageUploadUrlCommand(
    val memberId: Long,
    val contentType: String,
    val contentLength: Long,
)

/** 프로필 이미지 업로드 확인(R6). 발급받은 임시 키를 되돌려준다. */
data class ConfirmProfileImageUploadCommand(
    val memberId: Long,
    val objectKey: String,
)

/** 프로필 이미지 제거(R6). 이미 없어도 성공한다(멱등). */
data class RemoveProfileImageCommand(
    val memberId: Long,
)
