package org.around.ieuri.auth.application.token.required.dto
import java.time.Instant

/** 발급된 refreshToken(값 + 저장소 키가 되는 jti + 만료시각). */
data class IssuedRefreshToken(
    val token: String,
    val jti: String,
    val expiresAt: Instant,
)
