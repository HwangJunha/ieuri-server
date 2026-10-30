package org.around.ieuri.auth.application.token.required
import org.around.ieuri.auth.application.token.provided.dto.AccessTokenClaims
import org.around.ieuri.auth.application.token.required.dto.IssuedRefreshToken
import org.around.ieuri.auth.application.token.required.dto.IssuedToken
import org.around.ieuri.auth.application.token.required.dto.RefreshTokenClaims

/** 토큰 발급/검증 포트. refresh 검증 실패(서명/만료/타입)는 InvalidTokenException을 던진다. */
interface TokenProviderPort {
    /** accessToken 발급. 유효 권한·역할을 클레임에 담으므로 역할 변경은 최대 access TTL 뒤에 반영된다. */
    fun issueAccessToken(memberId: Long, permissions: Set<String>, roles: Set<String>): IssuedToken

    fun issueRefreshToken(memberId: Long): IssuedRefreshToken

    /** refresh 토큰을 파싱·검증하고 클레임을 반환. 유효하지 않으면 예외. */
    fun parseRefreshToken(token: String): RefreshTokenClaims

    /** access 토큰을 파싱·검증하고 인증 주체(memberId)를 반환. 유효하지 않으면 예외. */
    fun parseAccessToken(token: String): AccessTokenClaims
}
