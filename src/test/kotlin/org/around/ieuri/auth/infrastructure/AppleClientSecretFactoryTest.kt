package org.around.ieuri.auth.infrastructure

import io.jsonwebtoken.Jwts
import org.around.ieuri.auth.infrastructure.adapter.out.client.AppleClientSecretFactory
import org.around.ieuri.auth.infrastructure.config.SnsRevokeProperties
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 애플 client secret JWT 생성 검증(SPEC-0012 R8).
 *
 * 서명의 **유효성**은 애플만 판정할 수 있어 범위 밖이다. 여기서 고정하는 것은
 * 설정된 값(teamId/keyId/clientId)이 실제로 JWT에 실린다는 것과,
 * 환경변수로 주입되는 `\n` 이스케이프 PEM을 읽어 ES256 서명까지 간다는 것이다.
 */
class AppleClientSecretFactoryTest {

    private val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    /** 애플 .p8과 같은 형식(PKCS#8 PEM)을 만들고, 환경변수 주입처럼 개행을 리터럴 \n으로 바꾼다. */
    private val escapedPem: String = buildString {
        append("-----BEGIN PRIVATE KEY-----\n")
        append(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded))
        append("\n-----END PRIVATE KEY-----\n")
    }.replace("\n", "\\n")

    private fun factory(privateKey: String = escapedPem) = AppleClientSecretFactory(
        SnsRevokeProperties(
            apple = SnsRevokeProperties.Apple(
                teamId = "TEAM123456",
                keyId = "KEY7890123",
                clientId = "kr.ieuri.example",
                privateKey = privateKey,
            ),
        ),
    )

    private val now = Instant.parse("2026-08-08T00:00:00Z")

    @Test
    fun `설정된 팀 키 클라이언트 값이 JWT에 실린다`() {
        val secret = factory().create(now)

        val claims = Jwts.parser().verifyWith(keyPair.public).build().parseSignedClaims(secret)
        assertEquals("TEAM123456", claims.payload.issuer)
        assertEquals("kr.ieuri.example", claims.payload.subject)
        assertEquals("KEY7890123", claims.header["kid"])
        assertTrue(claims.payload.audience.contains("https://appleid.apple.com"))
    }

    @Test
    fun `개인키로 서명되어 공개키로 검증된다`() {
        // 파싱이 깨지면 여기서 예외가 난다 — .p8을 읽어 ES256까지 가는 경로를 고정한다.
        val secret = factory().create(now)
        assertEquals("ES256", Jwts.parser().verifyWith(keyPair.public).build().parseSignedClaims(secret).header.algorithm)
    }

    @Test
    fun `만료는 발급 시각 이후다`() {
        val claims = Jwts.parser().verifyWith(keyPair.public).build()
            .parseSignedClaims(factory().create(now)).payload
        assertEquals(now.epochSecond, claims.issuedAt.toInstant().epochSecond)
        assertTrue(claims.expiration.toInstant().isAfter(now))
    }

    @Test
    fun `개행이 그대로인 PEM도 읽는다`() {
        // 주입 방식에 따라 실제 개행으로 올 수도 있다. 둘 다 같은 키로 해석되어야 한다.
        val literalNewlinePem = escapedPem.replace("\\n", "\n")
        val secret = factory(literalNewlinePem).create(now)
        assertEquals("ES256", Jwts.parser().verifyWith(keyPair.public).build().parseSignedClaims(secret).header.algorithm)
    }
}
