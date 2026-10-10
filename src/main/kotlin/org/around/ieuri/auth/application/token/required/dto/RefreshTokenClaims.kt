package org.around.ieuri.auth.application.token.required.dto
import java.time.Instant

/** 검증된 refresh 토큰의 클레임. */
data class RefreshTokenClaims(
    val memberId: Long,
    val jti: String,
    val expiresAt: Instant,
)
