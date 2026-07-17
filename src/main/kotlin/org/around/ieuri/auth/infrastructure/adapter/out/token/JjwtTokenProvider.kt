package org.around.ieuri.auth.infrastructure.adapter.out.token

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.around.ieuri.auth.application.exception.InvalidTokenException
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

/**
 * jjwt 기반 JWT 토큰 제공자(HS256). accessToken은 stateless, refreshToken은 jti로 저장소에서 추적된다.
 * typ 클레임으로 access/refresh를 구분해 refresh 자리에 access를 넣는 오용을 막는다.
 */
@Component
class JjwtTokenProvider(
    properties: JwtProperties,
    private val clock: Clock,
) : TokenProviderPort {

    private val key: SecretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.secret))
    private val issuer = properties.issuer
    private val accessTtl = properties.accessTtl
    private val refreshTtl = properties.refreshTtl

    override fun issueAccessToken(memberId: Long): IssuedToken {
        val now = clock.instant()
        val expiresAt = now.plus(accessTtl)
        val token = Jwts.builder()
            .issuer(issuer)
            .subject(memberId.toString())
            .id(UUID.randomUUID().toString())
            .claim(TYPE_CLAIM, TYPE_ACCESS)
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

        if (claims.get(TYPE_CLAIM, String::class.java) != TYPE_REFRESH) {
            throw InvalidTokenException("refresh 토큰이 아닙니다.")
        }
        val memberId = claims.subject?.toLongOrNull()
            ?: throw InvalidTokenException("토큰에 회원 식별자가 없습니다.")
        val jti = claims.id ?: throw InvalidTokenException("토큰에 jti가 없습니다.")

        return RefreshTokenClaims(memberId = memberId, jti = jti, expiresAt = claims.expiration.toInstant())
    }

    private companion object {
        const val TYPE_CLAIM = "typ"
        const val TYPE_ACCESS = "access"
        const val TYPE_REFRESH = "refresh"
    }
}
