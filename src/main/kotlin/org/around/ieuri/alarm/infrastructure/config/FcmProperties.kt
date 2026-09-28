package org.around.ieuri.alarm.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.Resource
import java.time.Duration

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

    /**
     * FCM 호출 대기 상한. **기본값에 기대지 않는다** — google-http-client의 기본은 0이고
     * 그것은 무제한이라, 응답 없는 FCM이 발송 스레드를 영영 붙든다.
     *
     * 값의 근거는 사용자 대기가 아니라 워커 회수다(POL-040) — 발송은 이미 비동기라
     * 아무도 이 시간을 기다리지 않고, 상한의 목적은 큐가 막히지 않게 하는 것이다.
     */
    val timeout: Duration = Duration.ofSeconds(10),
) {
    fun isConfigured(): Boolean = serviceAccountLocation?.exists() == true
}
