package org.around.ieuri.auth.infrastructure.adapter.out.token

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.around.ieuri.auth.application.exception.InvalidTokenException
import org.around.ieuri.auth.application.port.out.token.AccessTokenClaims
import org.around.ieuri.auth.application.port.out.token.IssuedRefreshToken
import org.around.ieuri.auth.application.port.out.token.IssuedToken
import org.around.ieuri.auth.application.port.out.token.RefreshTokenClaims
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
import org.around.ieuri.auth.infrastructure.config.JwtProperties
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/** jjwt 기반 JWT 제공자(HS256). typ 클레임으로 access/refresh를 구분해 서로의 자리에 쓰지 못하게 한다. */
@Component
class JjwtTokenProvider(
    properties: JwtProperties,
    private val clock: Clock,
) : TokenProviderPort {

    private val key: SecretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.secret))
    private val issuer = properties.issuer
    private val accessTtl = properties.accessTtl
    private val refreshTtl = properties.refreshTtl

    override fun issueAccessToken(memberId: Long, permissions: Set<String>, roles: Set<String>): IssuedToken {
        val now = clock.instant()
        val expiresAt = now.plus(accessTtl)
        val token = Jwts.builder()
            .issuer(issuer)
            .subject(memberId.toString())
            .id(UUID.randomUUID().toString())
            .claim(TYPE_CLAIM, TYPE_ACCESS)
            // 유효 권한/역할을 flatten해 담는다. 인가는 요청당 DB 조회 없이 perms로 판정한다.
            .claim(PERMS_CLAIM, permissions.toList())
            .claim(ROLES_CLAIM, roles.toList())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key, Jwts.SIG.HS256)
            .compact()
        return IssuedToken(token = token, expiresAt = expiresAt)
    }

    override fun issueRefreshToken(memberId: Long): IssuedRefreshToken {
        val now = clock.instant()
        val expiresAt = now.plus(refreshTtl)
        val jti = UUID.randomUUID().toString()
        val token = Jwts.builder()
            .issuer(issuer)
            .subject(memberId.toString())
            .id(jti)
            .claim(TYPE_CLAIM, TYPE_REFRESH)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key, Jwts.SIG.HS256)
            .compact()
        return IssuedRefreshToken(token = token, jti = jti, expiresAt = expiresAt)
    }

    override fun parseRefreshToken(token: String): RefreshTokenClaims {
        val claims = parseAndVerify(token, TYPE_REFRESH)
        val memberId = claims.subject?.toLongOrNull()
            ?: throw InvalidTokenException("토큰에 회원 식별자가 없습니다.")
        val jti = claims.id ?: throw InvalidTokenException("토큰에 jti가 없습니다.")
        return RefreshTokenClaims(memberId = memberId, jti = jti, expiresAt = claims.expiration.toInstant())
    }

    override fun parseAccessToken(token: String): AccessTokenClaims {
        val claims = parseAndVerify(token, TYPE_ACCESS)
        val memberId = claims.subject?.toLongOrNull()
            ?: throw InvalidTokenException("토큰에 회원 식별자가 없습니다.")
        return AccessTokenClaims(
            memberId = memberId,
            permissions = claims.stringSet(PERMS_CLAIM),
            roles = claims.stringSet(ROLES_CLAIM),
        )
    }

    /** 문자열 리스트 클레임을 Set<String>으로 안전하게 읽는다(누락/타입 불일치 시 빈 집합). */
    private fun io.jsonwebtoken.Claims.stringSet(name: String): Set<String> = (this[name] as? Collection<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()

    /** 서명·발급자·만료를 검증하고 typ 클레임을 확인한 뒤 Claims를 반환. 실패 시 InvalidTokenException. */
    private fun parseAndVerify(token: String, expectedType: String): io.jsonwebtoken.Claims {
        val claims = try {
            Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (ex: JwtException) {
            throw InvalidTokenException("유효하지 않은 토큰입니다.")
        } catch (ex: IllegalArgumentException) {
            throw InvalidTokenException("유효하지 않은 토큰입니다.")
        }
        if (claims.get(TYPE_CLAIM, String::class.java) != expectedType) {
            throw InvalidTokenException("토큰 유형이 올바르지 않습니다.")
        }
        return claims
    }

    private companion object {
        const val TYPE_CLAIM = "typ"
        const val TYPE_ACCESS = "access"
        const val TYPE_REFRESH = "refresh"
        const val PERMS_CLAIM = "perms"
        const val ROLES_CLAIM = "roles"
    }
}
