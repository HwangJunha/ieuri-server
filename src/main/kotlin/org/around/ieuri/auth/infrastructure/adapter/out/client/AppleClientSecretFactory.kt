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
 * 애플 client secret 생성기. 애플은 client secret 문자열이 아니라 **ES256으로 직접 서명한 JWT**를 요구한다.
 * (iss=teamId, sub=clientId, aud=https://appleid.apple.com, kid 헤더=keyId)
 *
 * 개인키는 개발자 계정에서 받은 .p8 파일의 PEM 내용이며 환경변수로만 주입된다.
 */
@Component
class AppleClientSecretFactory(
    private val properties: SnsRevokeProperties,
) {
    fun create(now: Instant): String {
        val apple = properties.apple
        return Jwts.builder()
            .header().keyId(apple.keyId).and()
            .issuer(apple.teamId)
            .subject(apple.clientId)
            .audience().add(APPLE_AUDIENCE).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(SECRET_TTL_SECONDS)))
            .signWith(parsePrivateKey(apple.privateKey))
            .compact()
    }

    /**
     * PEM(.p8)에서 헤더/푸터와 개행을 걷어내고 PKCS#8 EC 개인키로 만든다.
     * 개행이 리터럴 `\n`으로 오는 것은 환경변수로 여러 줄을 주입하기 때문이다.
     *
     * PEM 라이브러리를 쓰지 않는다 — 클래스패스의 nimbus-jose-jwt가 파서를 제공하지만
     * BouncyCastle을 런타임에 요구하고, 그건 이 프로젝트에 없다. Apple .p8은 PKCS#8로
     * 고정이라 JDK 표준(KeyFactory)이 그대로 읽으므로 한 형식 때문에 암호 라이브러리를
     * 더할 이유가 없다. 다른 형식(PKCS#1, 암호화된 PEM)을 받아야 해지면 그때 도입한다.
     */
    private fun parsePrivateKey(pem: String): PrivateKey {
        val base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .filterNot { it.isWhitespace() }
        val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64))
        return KeyFactory.getInstance("EC").generatePrivate(keySpec)
    }

    private companion object {
        const val APPLE_AUDIENCE = "https://appleid.apple.com"
        const val SECRET_TTL_SECONDS = 300L
    }
}
