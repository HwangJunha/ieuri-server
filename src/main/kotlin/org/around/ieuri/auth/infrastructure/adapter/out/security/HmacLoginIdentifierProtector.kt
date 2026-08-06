package org.around.ieuri.auth.infrastructure.adapter.out.security

import org.around.ieuri.auth.application.port.out.security.LoginIdentifierProtectorPort
import org.around.ieuri.auth.infrastructure.config.EncryptionProperties
import org.springframework.stereotype.Component
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 로그인 식별자 보호 구현. pepper를 키로 한 HMAC-SHA256의 소문자 hex(64자)를 낸다.
 *
 * 결정론적이라 등호 조회와 (provider, provider_user_id) 유니크 제약이 그대로 동작한다.
 * identifierPepper가 바뀌면 기존 EMAIL 회원이 로그인 불가가 되므로 값을 교체하지 않는다(재이전 필요).
 * 그래서 교체 가능해야 하는 검증 코드용 pepper와 값을 공유하지 않는다.
 */
@Component
class HmacLoginIdentifierProtector(
    properties: EncryptionProperties,
) : LoginIdentifierProtectorPort {

    private val secretKey = SecretKeySpec(properties.identifierPepper.toByteArray(Charsets.UTF_8), ALGORITHM)

    override fun protect(normalizedIdentifier: String): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(secretKey) }
        val digest = mac.doFinal(normalizedIdentifier.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
    }
}
