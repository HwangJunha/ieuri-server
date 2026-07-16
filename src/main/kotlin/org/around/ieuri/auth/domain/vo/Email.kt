package org.around.ieuri.auth.domain.vo

/**
 * 이메일 값 객체. 정규화(trim + lowercase)하여 저장하므로
 * provider 검증 이메일과 사용자 제출 이메일을 대소문자 무관하게 동등 비교할 수 있다.
 * 개인정보이므로 영속 계층에서 암호화되어 저장된다(도메인은 평문만 다룬다).
 */
@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        operator fun invoke(raw: String): Email {
            val v = raw.trim().lowercase()
            require(v.isNotBlank()) { "이메일은 필수 입력값입니다." }
            require(v.length <= 320) { "이메일이 너무 깁니다." }
            require(EMAIL_REGEX.matches(v)) { "이메일 형식이 올바르지 않습니다: $raw" }
            return Email(v)
        }
    }
}
