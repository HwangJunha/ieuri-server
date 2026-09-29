package org.around.ieuri.community.application.policy

import java.time.Duration

/**
 * 커뮤니티 유스케이스의 설정값.
 * - [maxPageSize]·[maxRadiusMeters] 초과 요청은 오류가 아니라 상한으로 절삭된다.
 * - [allowedContentTypes] 밖의 요청에는 업로드 URL을 발급하지 않는다.
 */
data class CommunityPolicy(
    val maxPageSize: Int,
    val commentPreviewSize: Int,
    val maxRadiusMeters: Int,
    val uploadUrlTtl: Duration,
    val maxUploadBytes: Long,
    val allowedContentTypes: Set<String>,
)
