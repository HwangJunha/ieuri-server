package org.around.ieuri.auth.application.port.out.security

/** 개인정보 암호화 포트(AES-256). 키는 설정으로 주입되며 애플리케이션은 키를 알지 못한다. */
interface EmailEncryptorPort {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

/**
 * 로그인 식별자 보호 포트. EMAIL 회원의 provider_user_id(= 이메일)를 복원 불가능한 형태로 바꾼다.
 * - 같은 입력은 항상 같은 결과여야 한다. 등호 조회와 유니크 제약이 이 값으로 동작한다.
 * - 입력은 정규화된(trim+lowercase) 이메일이어야 한다. SNS 식별자는 변환하지 않는다.
 */
interface LoginIdentifierProtectorPort {
    fun protect(normalizedIdentifier: String): String
}

/** 6자리 검증 코드 생성/해시 포트. 해시만 영속화하고 입력 코드를 같은 방식으로 해시해 비교한다. */
interface VerificationCodeGeneratorPort {
    fun generate(): String
    fun hash(code: String): String
}

/** 비밀번호 단방향 해시 포트(구현은 BCrypt + 서버 pepper). */
interface PasswordEncoderPort {
    fun encode(rawPassword: String): String
    fun matches(rawPassword: String, encodedPassword: String): Boolean
}
