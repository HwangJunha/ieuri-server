package org.around.ieuri.community.application.photo.provided.dto

import java.time.Instant

/** 발급된 presigned URL 한 건. */
data class PostImageUploadUrlView(
    val uploadUrl: String,
    val objectKey: String,
    val expiresAt: Instant,
)
