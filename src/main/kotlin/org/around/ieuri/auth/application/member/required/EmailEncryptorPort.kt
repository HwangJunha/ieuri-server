package org.around.ieuri.auth.application.member.required
/** 개인정보 암호화 포트(AES-256). 키는 설정으로 주입되며 애플리케이션은 키를 알지 못한다. */
interface EmailEncryptorPort {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}
