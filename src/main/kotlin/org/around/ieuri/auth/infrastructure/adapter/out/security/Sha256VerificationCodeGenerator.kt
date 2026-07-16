package org.around.ieuri.auth.infrastructure.adapter.out.security

import org.around.ieuri.auth.application.port.out.security.VerificationCodeGeneratorPort
import org.around.ieuri.auth.infrastructure.config.EncryptionProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 6자리 검증 코드 생성/해시 구현. 코드는 평문 저장하지 않고 pepper를 섞은 SHA-256으로 해시해 저장한다.
 * 짧은 만료·시도 제한·단회용(도메인)과 함께 6자리 코드의 무차별 대입 위험을 낮춘다.
 */
@Component
class Sha256VerificationCodeGenerator(
    properties: EncryptionProperties,
) : VerificationCodeGeneratorPort {

    private val pepper = properties.pepper
    private val random = SecureRandom()

    override fun generate(): String = "%06d".format(random.nextInt(1_000_000))

    override fun hash(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$pepper:$code".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
