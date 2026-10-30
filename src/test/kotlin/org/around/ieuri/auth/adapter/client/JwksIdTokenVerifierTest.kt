package org.around.ieuri.auth.adapter.client
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.around.ieuri.auth.adapter.client.JwksIdTokenVerifier
import org.around.ieuri.auth.application.member.provided.exception.SnsVerificationFailedException
import java.security.interfaces.RSAPrivateKey
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** [JwksIdTokenVerifier] 단위 테스트. 테스트용 RSA 키로 JWKS를 만들어 서명·iss·aud·exp 판정을 본다. */
class JwksIdTokenVerifierTest {

    private val issuer = "https://accounts.google.com"
    private val audience = "web-client-id"

    private val rsaJwk: RSAKey = com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048)
        .keyID("test-key")
        .generate()

    private val jwkSource = ImmutableJWKSet<SecurityContext>(JWKSet(rsaJwk.toPublicJWK()))

    private fun verifier(
        issuers: Set<String> = setOf(issuer),
        auds: Set<String> = setOf(audience),
    ) = JwksIdTokenVerifier(jwkSource, issuers, auds)

    private fun signedToken(
        iss: String = issuer,
        aud: String = audience,
        sub: String = "user-123",
        exp: Instant = Instant.now().plusSeconds(300),
        signingKey: RSAKey = rsaJwk,
        extra: Map<String, Any> = emptyMap(),
    ): String {
        val claimsBuilder = JWTClaimsSet.Builder()
            .subject(sub)
            .issuer(iss)
            .audience(aud)
            .expirationTime(Date.from(exp))
            .jwtID(UUID.randomUUID().toString())
        extra.forEach { (k, v) -> claimsBuilder.claim(k, v) }
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.keyID).build(),
            claimsBuilder.build(),
        )
        jwt.sign(RSASSASigner(signingKey.toRSAPrivateKey() as RSAPrivateKey))
        return jwt.serialize()
    }

    @Test
    fun `verifies a well-formed token and returns claims`() {
        val claims = verifier().verify(
            signedToken(extra = mapOf("email" to "a@b.com", "email_verified" to true)),
        )
        assertEquals("user-123", claims.subject)
        assertEquals("a@b.com", claims.getStringClaim("email"))
    }

    @Test
    fun `rejects a wrong audience`() {
        assertFailsWith<SnsVerificationFailedException> {
            verifier().verify(signedToken(aud = "someone-else"))
        }
    }

    @Test
    fun `accepts any audience in the allowed set (iOS vs web client id)`() {
        // Google's id_token aud differs by platform: iOS client id from an iOS
        // app, web client id from Android/web. Both must pass.
        val iosId = "ios-client-id"
        val webId = "web-client-id"
        val v = verifier(auds = setOf(webId, iosId))
        assertEquals("user-123", v.verify(signedToken(aud = iosId)).subject)
        assertEquals("user-123", v.verify(signedToken(aud = webId)).subject)
    }

    @Test
    fun `rejects a wrong issuer`() {
        assertFailsWith<SnsVerificationFailedException> {
            verifier().verify(signedToken(iss = "https://evil.example.com"))
        }
    }

    @Test
    fun `rejects an expired token`() {
        assertFailsWith<SnsVerificationFailedException> {
            verifier().verify(signedToken(exp = Instant.now().minusSeconds(60)))
        }
    }

    @Test
    fun `rejects a token signed by an unknown key`() {
        val strangerKey = com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048)
            .keyID("stranger")
            .generate()
        assertFailsWith<SnsVerificationFailedException> {
            verifier().verify(signedToken(signingKey = strangerKey))
        }
    }
}
