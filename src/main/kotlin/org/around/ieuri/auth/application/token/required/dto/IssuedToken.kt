package org.around.ieuri.auth.application.token.required.dto
import java.time.Instant

/** 발급된 accessToken(값 + 만료시각). */
data class IssuedToken(
    val token: String,
    val expiresAt: Instant,
)
