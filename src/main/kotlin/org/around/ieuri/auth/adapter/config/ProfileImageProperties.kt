package org.around.ieuri.auth.adapter.config
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 프로필 이미지 업로드 설정(SPEC-0011 R6). 버킷은 community와 같아도 된다(경로가 `profile/`로 갈린다). */
@ConfigurationProperties(prefix = "ieuri.profile-image")
data class ProfileImageProperties(
    val bucket: String = "",
    /** presigned URL 유효 기간. 모바일 네트워크에서 대용량 사진은 느리다. */
    val uploadUrlTtl: Duration = Duration.ofMinutes(30),
    val maxUploadBytes: Long = 10L * 1024 * 1024,
    val allowedContentTypes: List<String> = listOf("image/jpeg", "image/png", "image/webp"),
)
