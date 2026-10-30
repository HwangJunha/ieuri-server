package org.around.ieuri.auth.application.member
import java.time.Duration

/**
 * 프로필 이미지 업로드 설정(SPEC-0011 R6).
 * - 허용 MIME 밖의 요청에는 URL을 발급하지 않는다.
 */
data class ProfileImagePolicy(
    val uploadUrlTtl: Duration,
    val maxUploadBytes: Long,
    val allowedContentTypes: Set<String>,
)
