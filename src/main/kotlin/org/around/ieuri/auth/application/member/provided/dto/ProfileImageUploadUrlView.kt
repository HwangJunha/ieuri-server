package org.around.ieuri.auth.application.member.provided.dto
import java.time.Instant

/** 프로필 이미지 업로드 URL 발급 결과(R6). */
data class ProfileImageUploadUrlView(
    val uploadUrl: String,
    val objectKey: String,
    val expiresAt: Instant,
)
