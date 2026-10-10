package org.around.ieuri.auth.adapter.security
import org.around.ieuri.auth.adapter.config.EncryptionProperties
import org.around.ieuri.auth.application.member.required.VerificationCodeGeneratorPort
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom

/** 6자리 검증 코드 생성/해시 구현. 코드는 pepper를 섞은 SHA-256 해시로만 저장한다. */
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
