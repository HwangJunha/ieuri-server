package org.around.ieuri.auth.application.port.out.security

/**
 * 개인정보 암호화 포트. 이메일 등은 AES-256으로 암호화하여 저장한다.
 * 키는 프로파일별 설정/환경변수로 주입된다(도메인/애플리케이션은 키를 알지 못한다).
 */
interface EmailEncryptorPort {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

/**
 * 6자리 검증 코드 생성/해시 포트.
 * 코드는 평문으로 저장하지 않으므로 hash 결과만 영속화하고, 검증 시 입력 코드를 같은 방식으로 해시해 비교한다.
 */
interface VerificationCodeGeneratorPort {
    fun generate(): String
    fun hash(code: String): String
}
