package org.around.ieuri.auth.infrastructure.adapter.out.security

import org.around.ieuri.auth.application.port.out.security.EmailEncryptorPort
import org.around.ieuri.auth.infrastructure.config.EncryptionProperties
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM 개인정보 암호화 구현. 저장 포맷은 base64(iv(12B) || ciphertext+tag).
 * 키는 프로파일별 설정/환경변수로 주입된 base64 32바이트 키다(EncryptionProperties).
 */
@Component
class AesGcmEmailEncryptor(
    properties: EncryptionProperties,
) : EmailEncryptorPort {

    private val secretKey: SecretKeySpec
    private val random = SecureRandom()

    init {
        val keyBytes = Base64.getDecoder().decode(properties.key)
        require(keyBytes.size == 32) { "암호화 키는 AES-256용 32바이트(base64)여야 합니다. 실제: ${keyBytes.size}B" }
        secretKey = SecretKeySpec(keyBytes, "AES")
    }

    override fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
        }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    override fun decrypt(ciphertext: String): String {
        val decoded = Base64.getDecoder().decode(ciphertext)
        val iv = decoded.copyOfRange(0, IV_LENGTH)
        val body = decoded.copyOfRange(IV_LENGTH, decoded.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
        }
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
