package org.around.ieuri.auth.adapter.config
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * JWT 설정. prod는 환경변수 주입(기본값 없음 → 부팅 실패), local은 개발 전용 값.
 * - secret: HS256 서명 키(base64, 32바이트 이상)
 * - accessTtl/refreshTtl: 예) 5m, 30d
 */
@ConfigurationProperties(prefix = "ieuri.jwt")
data class JwtProperties(
    val issuer: String,
    val secret: String,
    val accessTtl: Duration,
    val refreshTtl: Duration,
)
