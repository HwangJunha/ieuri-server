package org.around.ieuri.auth.application.policy

import java.time.Duration

/**
 * 프로필 이미지 업로드 설정(SPEC-0011 R6). 인프라 @ConfigurationProperties에서 뽑아 온
 * 프레임워크 무의존 데이터로, 서비스가 이것만 주입받는다.
 *
 * @param uploadUrlTtl        presigned URL 유효 기간.
 * @param maxUploadBytes      업로드 허용 최대 바이트.
 * @param allowedContentTypes 허용 MIME 타입. 이 외에는 URL을 발급하지 않는다.
 */
data class ProfileImagePolicy(
    val uploadUrlTtl: Duration,
    val maxUploadBytes: Long,
    val allowedContentTypes: Set<String>,
)
