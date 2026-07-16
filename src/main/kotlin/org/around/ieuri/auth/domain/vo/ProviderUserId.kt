package org.around.ieuri.auth.domain.vo

/**
 * SNS provider가 부여한 사용자 고유 식별자(google sub, apple sub, kakao id 등).
 * (provider, providerUserId) 조합이 회원의 자연 키다.
 */
@JvmInline
value class ProviderUserId private constructor(val value: String) {
    companion object {
        operator fun invoke(raw: String): ProviderUserId {
            val v = raw.trim()
            require(v.isNotBlank()) { "providerUserId는 비어 있을 수 없습니다." }
            require(v.length <= 255) { "providerUserId가 너무 깁니다." }
            return ProviderUserId(v)
        }
    }
}
