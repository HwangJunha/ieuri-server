package org.around.ieuri.community.application.photo.provided.dto

/** 여러 장을 한 번에 발급한다. 하나라도 어긋나면 전부 발급하지 않는다. */
data class IssuePostImageUploadUrlsCommand(val images: List<PostImageUploadRequest>)
