package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * JWT 설정. 서명 비밀키는 비밀값으로, prod는 환경변수 주입(기본값 없이 fail-fast),
 * local은 개발 전용 값을 사용한다.
 * - secret: HS256 서명 키(base64 인코딩, 32바이트 이상)
 * - accessTtl/refreshTtl: 예) 5m, 30d
 */
@ConfigurationProperties(prefix = "ieuri.jwt")
data class JwtProperties(
    val issuer: String,
    val secret: String,
    val accessTtl: Duration,
    val refreshTtl: Duration,
)
