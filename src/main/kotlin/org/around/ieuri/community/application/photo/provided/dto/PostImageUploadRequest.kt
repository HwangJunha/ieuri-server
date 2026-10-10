package org.around.ieuri.community.application.photo.provided.dto

/** presigned URL 발급 입력. 서명에 포함되므로 업로드 시 이 값과 정확히 일치해야 한다. */
data class PostImageUploadRequest(
    val contentType: String,
    val contentLength: Long,
)
