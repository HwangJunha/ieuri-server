package org.around.ieuri.tourism.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Voyage 임베딩 API 접근 설정(SPEC-0035). [apiKey]는 환경변수로만 주입한다(응답·로그·GraphQL 노출 금지).
 * - 빈 값이면 기동은 되고 임베딩 단계만 실패로 센다.
 */
@ConfigurationProperties(prefix = "ieuri.voyage")
data class VoyageProperties(
    val apiKey: String = "",
    val baseUrl: String = "https://api.voyageai.com",
    val connectTimeoutMillis: Long = 5_000,
    val readTimeoutMillis: Long = 60_000,
)
