package org.around.ieuri.auth.application.port.out.token

import java.time.Instant

/**
 * 토큰 발급/검증 포트. JWT는 인프라 관심사이므로 구현(JwtTokenProvider)에 위임한다.
 * refresh 검증 실패(서명/만료/타입 불일치)는 InvalidTokenException을 던진다.
 */
interface TokenProviderPort {
    /**
     * accessToken 발급. 발급 시점의 유효 권한(permissions)과 역할(roles)을 클레임에 flatten해 담는다.
     * 인가는 요청당 DB 조회 없이 이 토큰의 permissions로 판정한다(역할 변경은 최대 access TTL 후 반영).
     */
    fun issueAccessToken(memberId: Long, permissions: Set<String>, roles: Set<String>): IssuedToken

    fun issueRefreshToken(memberId: Long): IssuedRefreshToken

    /** refresh 토큰을 파싱·검증하고 클레임을 반환. 유효하지 않으면 예외. */
    fun parseRefreshToken(token: String): RefreshTokenClaims

    /** access 토큰을 파싱·검증하고 인증 주체(memberId)를 반환. 유효하지 않으면 예외. */
    fun parseAccessToken(token: String): AccessTokenClaims
}

/** 검증된 access 토큰의 클레임. permissions로 인가를 판정하고, roles는 표시/감사용이다. */
data class AccessTokenClaims(
    val memberId: Long,
    val permissions: Set<String>,
    val roles: Set<String>,
)

/** 발급된 accessToken(값 + 만료시각). */
data class IssuedToken(
    val token: String,
    val expiresAt: Instant,
)

/** 발급된 refreshToken(값 + 저장소 키가 되는 jti + 만료시각). */
data class IssuedRefreshToken(
    val token: String,
    val jti: String,
    val expiresAt: Instant,
)

/** 검증된 refresh 토큰의 클레임. */
data class RefreshTokenClaims(
    val memberId: Long,
    val jti: String,
    val expiresAt: Instant,
)
