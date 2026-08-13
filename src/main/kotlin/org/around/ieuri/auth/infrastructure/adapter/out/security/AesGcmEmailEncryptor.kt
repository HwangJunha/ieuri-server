package org.around.ieuri.auth.infrastructure.adapter.out.security

import org.around.ieuri.auth.application.port.out.security.EmailEncryptorPort
import org.around.ieuri.auth.infrastructure.config.EncryptionProperties
import org.around.ieuri.common.security.AesGcmCipher
import org.springframework.stereotype.Component

/**
 * 이메일 암호화 어댑터. 알고리즘은 [AesGcmCipher]가 갖고 여기는 포트에 잇기만 한다 —
 * community의 DM 본문 암호화가 같은 구현을 쓰므로 common으로 올렸다(SPEC-0017).
 */
@Component
class AesGcmEmailEncryptor(
    properties: EncryptionProperties,
) : EmailEncryptorPort {

    private val cipher = AesGcmCipher(properties.key)

    override fun encrypt(plaintext: String): String = cipher.encrypt(plaintext)

    override fun decrypt(ciphertext: String): String = cipher.decrypt(ciphertext)
}
