package org.around.ieuri.alarm.infrastructure.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * alarm 도메인 인프라 빈 구성. `@ConfigurationPropertiesScan`이 없으므로 여기서 명시적으로
 * `@EnableConfigurationProperties` 한다(프로젝트 관례).
 */
@Configuration
@EnableConfigurationProperties(FcmProperties::class)
class AlarmConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * FCM 발송 클라이언트. **자격증명이 없으면 null이다** — 기동은 되고 발송만 실패한다(§8).
     *
     * `FirebaseApp`은 이름당 하나뿐인 전역 등록이라, 이미 있으면 그것을 다시 쓴다.
     * 그러지 않으면 컨텍스트가 두 번 뜨는 상황에서 `IllegalStateException`으로 기동이 깨진다.
     */
    @Bean
    fun firebaseMessaging(properties: FcmProperties): FirebaseMessaging? {
        if (!properties.isConfigured()) {
            log.warn("FCM 자격증명이 없어 푸시 발송이 비활성화된다(ieuri.fcm.service-account-location).")
            return null
        }
        return try {
            val app = FirebaseApp.getApps()
                .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                ?: FirebaseApp.initializeApp(
                    FirebaseOptions.builder()
                        .setCredentials(
                            requireNotNull(properties.serviceAccountLocation).inputStream.use {
                                GoogleCredentials.fromStream(it)
                            },
                        )
                        // **상한을 명시해야 한다.** 기본값 0은 무제한이라, 응답 없는 FCM이
                        // 발송 워커를 영영 붙들어 큐가 막힌다.
                        .setConnectTimeout(properties.timeout.toMillis().toInt())
                        .setReadTimeout(properties.timeout.toMillis().toInt())
                        .setWriteTimeout(properties.timeout.toMillis().toInt())
                        .build(),
                )
            FirebaseMessaging.getInstance(app)
        } catch (e: Exception) {
            // 자격증명이 잘못돼도 기동을 막지 않는다 — 실패 범위는 알림에 한정된다.
            log.error("FCM 초기화에 실패해 푸시 발송이 비활성화된다.", e)
            null
        }
    }
}
