package org.around.ieuri.auth.infrastructure

import io.jsonwebtoken.Jwts
import org.around.ieuri.auth.infrastructure.adapter.out.client.AppleClientSecretFactory
import org.around.ieuri.auth.infrastructure.config.SnsRevokeProperties
import org.springframework.core.io.ByteArrayResource
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 애플 client secret JWT 생성(SPEC-0012 R8). 서명 유효성은 애플만 판정할 수 있어 범위 밖이다.
 * - 설정값(teamId/keyId/clientId)이 JWT에 실리고, PEM을 읽어 ES256 서명까지 가는지 고정한다.
 */
class AppleClientSecretFactoryTest {

    private val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    /** 애플 .p8과 같은 형식(PKCS#8 PEM). 실제 파일처럼 Resource로 넘긴다. */
    private val pem: String = buildString {
        append("-----BEGIN PRIVATE KEY-----\n")
        append(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded))
        append("\n-----END PRIVATE KEY-----\n")
    }

    private fun factory(privateKeyPem: String = pem) = AppleClientSecretFactory(
        SnsRevokeProperties(
            apple = SnsRevokeProperties.Apple(
                teamId = "TEAM123456",
                keyId = "KEY7890123",
                clientId = "kr.ieuri.example",
                privateKeyLocation = ByteArrayResource(privateKeyPem.toByteArray()),
            ),
        ),
    )

    /** 고정 시각을 쓰지 않는다 — client secret은 5분 만료라 과거로 고정하면 파서가 만료로 거부한다. */
    private val now: Instant = Instant.now()

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
    fun `만료는 발급 5분 뒤다`() {
        val claims = Jwts.parser().verifyWith(keyPair.public).build()
            .parseSignedClaims(factory().create(now)).payload
        assertEquals(now.epochSecond, claims.issuedAt.toInstant().epochSecond)
        // 애플은 6개월까지 허용하나 짧게 잡는다 — 매 호출 새로 만들므로 길게 둘 이유가 없다.
        assertEquals(now.epochSecond + 300, claims.expiration.toInstant().epochSecond)
    }

    @Test
    fun `키 경로가 없으면 설정되지 않은 것으로 본다`() {
        // 자격증명 미설정 시 해제를 시도하지 않고 실패로 기록만 남긴다(R11).
        val notConfigured = SnsRevokeProperties.Apple(
            teamId = "TEAM123456",
            keyId = "KEY7890123",
            clientId = "kr.ieuri.example",
            privateKeyLocation = null,
        )
        assertFalse(notConfigured.isConfigured())
        assertFailsWith<IllegalArgumentException> {
            AppleClientSecretFactory(SnsRevokeProperties(apple = notConfigured)).create(now)
        }
    }
}
