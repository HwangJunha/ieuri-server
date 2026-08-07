package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 프로필 이미지 업로드 설정(SPEC-0011 R6).
 *
 * **버킷은 community와 같은 값을 가리켜도 무방하다** — 경로가 `profile/` 구간으로 갈려
 * 오브젝트가 섞이지 않고, lifecycle rule은 접두어별로 건다. 별도 프로퍼티로 두는 이유는
 * 나중에 프로필만 다른 버킷으로 옮길 수 있어야 하기 때문이다.
 *
 * 조회용 공개 도메인은 여기 두지 않는다 — DB에는 object key만 저장하고 클라이언트가
 * CDN 베이스와 조합해 쓴다.
 */
@ConfigurationProperties(prefix = "ieuri.profile-image")
data class ProfileImageProperties(
    val bucket: String = "",
    /** presigned URL 유효 기간. 모바일 네트워크에서 대용량 사진은 느리다. */
    val uploadUrlTtl: Duration = Duration.ofMinutes(30),
    val maxUploadBytes: Long = 10L * 1024 * 1024,
    val allowedContentTypes: List<String> = listOf("image/jpeg", "image/png", "image/webp"),
)
