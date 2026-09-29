package org.around.ieuri.alarm.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.Resource
import java.time.Duration

/**
 * FCM 발송 설정(SPEC-0028, POL-046).
 * - 자격증명이 없어도 애플리케이션은 기동하며 발송만 건너뛴다.
 */
@ConfigurationProperties(prefix = "ieuri.fcm")
data class FcmProperties(
    /** 서비스 계정 JSON 경로(`file:/...` 또는 `classpath:...`). 파일은 저장소에 커밋하지 않는다. */
    val serviceAccountLocation: Resource? = null,

    /** FCM 호출 대기 상한(POL-040). google-http-client 기본값 0은 무제한이라 반드시 지정한다. */
    val timeout: Duration = Duration.ofSeconds(10),
) {
    fun isConfigured(): Boolean = serviceAccountLocation?.exists() == true
}
