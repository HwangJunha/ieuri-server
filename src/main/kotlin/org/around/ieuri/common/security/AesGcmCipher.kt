package org.around.ieuri.common.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM 대칭 암복호화. 저장 포맷은 base64(iv(12B) || ciphertext+tag)다.
 * - IV가 매번 달라 같은 평문도 결과가 다르다 — 이 값으로 등호 조회·유니크 제약을 걸 수 없다(그 용도는 HMAC).
 */
class AesGcmCipher(base64Key: String) {

    private val secretKey: SecretKeySpec
    private val random = SecureRandom()

    init {
        val keyBytes = Base64.getDecoder().decode(base64Key)
        require(keyBytes.size == KEY_BYTES) {
            "암호화 키는 AES-256용 ${KEY_BYTES}바이트(base64)여야 합니다. 실제: ${keyBytes.size}B"
        }
        secretKey = SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
        }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    fun decrypt(ciphertext: String): String {
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
        const val KEY_BYTES = 32
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
