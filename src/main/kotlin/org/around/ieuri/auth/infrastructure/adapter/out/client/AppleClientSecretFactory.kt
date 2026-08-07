package org.around.ieuri.auth.infrastructure.adapter.out.client

import io.jsonwebtoken.Jwts
import org.around.ieuri.auth.infrastructure.config.SnsRevokeProperties
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.springframework.stereotype.Component
import java.io.StringReader
import java.security.PrivateKey
import java.time.Instant
import java.util.Date

/**
 * 애플 client secret 생성기. 애플은 client secret 문자열이 아니라 **ES256으로 서명한 JWT**를 요구한다.
 * (iss=teamId, sub=clientId, aud=https://appleid.apple.com, kid 헤더=keyId)
 *
 * 개인키(.p8)는 파일 경로로 받아 기동 시 한 번만 읽어 캐시한다 — 탈퇴마다 디스크를 읽을
 * 이유가 없고, 키가 없거나 형식이 깨졌으면 첫 탈퇴가 아니라 기동 시점에 드러나는 편이 낫다.
 * k8s/k3s는 Secret을 볼륨으로 마운트하고 그 경로를 준다.
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
     * PEM(.p8)을 EC 개인키로 읽는다.
     *
     * nimbus의 `JWK.parseFromPEMEncodedObjects`는 쓸 수 없다 — .p8은 개인키만 담고 있는데
     * 그 API는 JWK 조립에 공개키를 함께 요구한다("Missing PEM-encoded public key").
     */
    private fun parsePrivateKey(pem: String): PrivateKey {
        val keyInfo = PEMParser(StringReader(pem)).use { it.readObject() } as PrivateKeyInfo
        return JcaPEMKeyConverter().getPrivateKey(keyInfo)
    }

    private companion object {
        const val APPLE_AUDIENCE = "https://appleid.apple.com"
        const val SECRET_TTL_SECONDS = 300L
    }
}
