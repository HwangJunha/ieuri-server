package org.around.ieuri.community.application.photo

import java.time.Duration

/** 사진 업로드 설정값. [allowedContentTypes] 밖의 요청에는 업로드 URL을 발급하지 않는다. */
data class PhotoPolicy(
    val uploadUrlTtl: Duration,
    val maxUploadBytes: Long,
    val allowedContentTypes: Set<String>,
)
