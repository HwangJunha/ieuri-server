package org.around.ieuri.auth.application.member.required
/** 6자리 검증 코드 생성/해시 포트. 해시만 영속화하고 입력 코드를 같은 방식으로 해시해 비교한다. */
interface VerificationCodeGeneratorPort {
    fun generate(): String
    fun hash(code: String): String
}
