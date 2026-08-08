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
 * 로그인 식별자 보호 포트. `Provider.EMAIL` 회원의 provider_user_id(= 이메일)를
 * 저장·조회 전에 복원 불가능한 형태로 바꾼다.
 *
 * 같은 입력은 항상 같은 결과여야 한다 — 등호 조회와 유니크 제약이 이 값으로 동작하기 때문이다.
 * 입력은 정규화된(trim+lowercase) 이메일을 전제한다. 정규화는 Email VO가 선행한다.
 * SNS 회원 식별자는 provider가 준 불투명 값이라 보호 대상이 아니다(변환하지 않는다).
 */
interface LoginIdentifierProtectorPort {
    fun protect(normalizedIdentifier: String): String
}

/**
 * 6자리 검증 코드 생성/해시 포트.
 * 코드는 평문으로 저장하지 않으므로 hash 결과만 영속화하고, 검증 시 입력 코드를 같은 방식으로 해시해 비교한다.
 */
interface VerificationCodeGeneratorPort {
    fun generate(): String
    fun hash(code: String): String
}

/**
 * 비밀번호 단방향 해시 포트. 자체(EMAIL) 회원가입/로그인에서 비밀번호를 안전하게 저장·검증한다.
 * 구현은 안전한 단방향 해시(BCrypt) + 서버 pepper를 사용한다(도메인/애플리케이션은 방식을 알지 못한다).
 */
interface PasswordEncoderPort {
    fun encode(rawPassword: String): String
    fun matches(rawPassword: String, encodedPassword: String): Boolean
}
