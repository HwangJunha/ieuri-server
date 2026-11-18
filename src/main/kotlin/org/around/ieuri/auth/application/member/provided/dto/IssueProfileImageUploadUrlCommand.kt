package org.around.ieuri.auth.application.member.provided.dto
/** 프로필 이미지 업로드 URL 발급(R6). 선언한 형식·크기가 서명에 포함된다. */
data class IssueProfileImageUploadUrlCommand(
    val contentType: String,
    val contentLength: Long,
)
