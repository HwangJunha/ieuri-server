package org.around.ieuri.community.application.photo.provided.dto

/** presigned URL 발급 입력. 서명에 포함되므로 업로드 시 이 값과 정확히 일치해야 한다. */
data class PostImageUploadRequest(
    val contentType: String,
    val contentLength: Long,
)

/** 여러 장을 한 번에 발급한다. 하나라도 어긋나면 전부 발급하지 않는다. */
data class IssuePostImageUploadUrlsCommand(val images: List<PostImageUploadRequest>)

/** 여러 장을 한 번에 확인한다. 장별로 성패가 갈린다. */
data class ConfirmPostImageUploadsCommand(val objectKeys: List<String>)

data class DeletePostImageCommand(val objectKey: String)
