package org.around.ieuri.alarm.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.Resource

/**
 * FCM 발송 설정(SPEC-0028 §8, POL-046 준용).
 *
 * **자격증명이 없어도 애플리케이션은 기동한다** — 부재 시 발송만 실패하고 그 범위는 알림에
 * 한정된다. 암호화 키처럼 fail-fast로 두지 않는 이유다.
 *
 * 서비스 계정 JSON은 **파일 경로로 받는다.** 환경변수에 JSON을 통째로 넣으면 개행 이스케이프가
 * 깨지기 쉽고 `/proc/<pid>/environ`에 노출된다 — 애플 `.p8`과 같은 판단이다.
 */
@ConfigurationProperties(prefix = "ieuri.fcm")
data class FcmProperties(
    /** 서비스 계정 JSON 경로(`file:/...` 또는 `classpath:...`). 파일은 저장소에 커밋하지 않는다. */
    val serviceAccountLocation: Resource? = null,
) {
    fun isConfigured(): Boolean = serviceAccountLocation?.exists() == true
}
