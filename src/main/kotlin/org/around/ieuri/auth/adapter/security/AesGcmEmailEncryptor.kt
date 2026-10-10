package org.around.ieuri.auth.adapter.security
import org.around.ieuri.auth.adapter.config.EncryptionProperties
import org.around.ieuri.auth.application.member.required.EmailEncryptorPort
import org.around.ieuri.common.security.AesGcmCipher
import org.springframework.stereotype.Component

/** 이메일 암호화 어댑터. 알고리즘은 [AesGcmCipher]가 갖고 여기는 포트에 잇기만 한다. */
@Component
class AesGcmEmailEncryptor(
    properties: EncryptionProperties,
) : EmailEncryptorPort {

    private val cipher = AesGcmCipher(properties.key)

    override fun encrypt(plaintext: String): String = cipher.encrypt(plaintext)

    override fun decrypt(ciphertext: String): String = cipher.decrypt(ciphertext)
}
