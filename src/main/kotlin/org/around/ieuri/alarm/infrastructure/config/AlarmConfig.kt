package org.around.ieuri.alarm.infrastructure.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** alarm 도메인 인프라 빈 구성. */
@Configuration
@EnableConfigurationProperties(FcmProperties::class)
class AlarmConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * FCM 발송 클라이언트. 자격증명이 없거나 초기화에 실패하면 null이다.
     * - `FirebaseApp`은 이름당 전역 하나라 이미 있으면 재사용한다. 새로 만들면 기동이 깨진다.
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
                        // 기본값 0은 무제한이라 상한을 명시해야 한다.
                        .setConnectTimeout(properties.timeout.toMillis().toInt())
                        .setReadTimeout(properties.timeout.toMillis().toInt())
                        .setWriteTimeout(properties.timeout.toMillis().toInt())
                        .build(),
                )
            FirebaseMessaging.getInstance(app)
        } catch (e: Exception) {
            // 자격증명이 잘못돼도 기동을 막지 않는다.
            log.error("FCM 초기화에 실패해 푸시 발송이 비활성화된다.", e)
            null
        }
    }
}
