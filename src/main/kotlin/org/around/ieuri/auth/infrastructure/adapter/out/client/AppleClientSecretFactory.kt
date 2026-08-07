package org.around.ieuri.auth.infrastructure.adapter.out.client

import io.jsonwebtoken.Jwts
import org.around.ieuri.auth.infrastructure.config.SnsRevokeProperties
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * 애플 client secret 생성기. 애플은 client secret 문자열이 아니라 **ES256으로 서명한 JWT**를 요구한다.
 * (iss=teamId, sub=clientId, aud=https://appleid.apple.com, kid 헤더=keyId)
 *
 * 개인키(.p8)는 기동 시 한 번만 읽어 캐시한다 — 탈퇴마다 디스크를 읽을 이유가 없고,
 * 키가 없거나 형식이 깨졌으면 첫 탈퇴가 아니라 기동 시점에 드러나는 편이 낫다.
 */
@Component
class AppleClientSecretFactory(
    private val properties: SnsRevokeProperties,
) {
    /** 자격증명 미설정이면 null이다(해제를 시도하지 않고 실패로 기록만 남긴다. R11). */
    private val privateKey: PrivateKey? = properties.apple
        .takeIf { it.isConfigured() }
        ?.privateKeyLocation
        ?.let { resource ->
            parsePrivateKey(resource.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
        }

    fun create(now: Instant): String {
        val key = requireNotNull(privateKey) { "애플 revoke 개인키가 설정되지 않았습니다." }
        val apple = properties.apple
        return Jwts.builder()
            .header().keyId(apple.keyId).and()
            .issuer(apple.teamId)
            .subject(apple.clientId)
            .audience().add(APPLE_AUDIENCE).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(SECRET_TTL_SECONDS)))
            .signWith(key)
            .compact()
    }

    /**
     * PEM(.p8)에서 헤더/푸터를 걷어내고 PKCS#8 EC 개인키로 만든다.
     *
     * PEM 라이브러리를 쓰지 않는다 — 애플 .p8은 **개인키만** 담긴 PKCS#8이라
     * nimbus의 `JWK.parseFromPEMEncodedObjects`가 공개키를 요구하며 거부하고
     * ("Missing PEM-encoded public key"), BouncyCastle을 직접 쓰면 암호 라이브러리가
     * 컴파일 의존성으로 올라온다. PKCS#8은 JDK KeyFactory가 그대로 읽는다.
     */
    private fun parsePrivateKey(pem: String): PrivateKey {
        val base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .filterNot { it.isWhitespace() }
        val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64))
        return KeyFactory.getInstance("EC").generatePrivate(keySpec)
    }

    private companion object {
        const val APPLE_AUDIENCE = "https://appleid.apple.com"
        const val SECRET_TTL_SECONDS = 300L
    }
}
