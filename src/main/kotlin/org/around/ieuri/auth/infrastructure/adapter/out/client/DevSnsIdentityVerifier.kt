package org.around.ieuri.auth.infrastructure.adapter.out.client

import org.around.ieuri.auth.application.port.out.client.SnsIdentity
import org.around.ieuri.auth.application.port.out.client.SnsIdentityVerifierPort
import org.around.ieuri.auth.domain.vo.Provider
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.Base64

/**
 * 개발/임시용 SNS 신원 검증기. id_token의 payload(JWT라면 두 번째 세그먼트, 아니면 전체)를
 * base64url 디코드하여 클레임을 추출한다. **서명/발급자(iss)/audience를 검증하지 않는다.**
 *
 * TODO(prod): provider별 실제 검증 어댑터로 교체한다.
 *   - google/apple: JWK로 서명검증 + iss/aud/exp 검증(GoogleIdTokenVerifier / Apple public keys)
 *   - kakao: 토큰으로 사용자정보 API 조회 후 app id 검증
 *   교체 시 이 클래스에 @Profile("local") 또는 @Profile("!prod")를 걸고 prod 구현을 추가한다.
 */
@Component
class DevSnsIdentityVerifier(
    private val objectMapper: ObjectMapper,
) : SnsIdentityVerifierPort {

    override fun verify(provider: Provider, idToken: String): SnsIdentity {
        val claims = decodeClaims(idToken)

        val providerUserId = firstNonBlank(claims, "sub", "id", "provider_user_id")
            ?: error("id_token에서 사용자 식별자를 찾을 수 없습니다.")
        val email = firstNonBlank(claims, "email")
        val emailVerified = when (val v = claims["email_verified"]) {
            is Boolean -> v
            is String -> v.toBoolean()
            else -> false
        }
        val nickname = firstNonBlank(claims, "nickname", "name", "given_name")
        val profileImageUrl = firstNonBlank(claims, "picture", "profile_image_url")

        return SnsIdentity(
            providerUserId = providerUserId,
            email = email,
            emailVerified = emailVerified,
            nickname = nickname,
            profileImageUrl = profileImageUrl,
        )
    }

    private fun decodeClaims(idToken: String): Map<String, Any?> {
        val segments = idToken.split(".")
        val payloadSegment = if (segments.size >= 2) segments[1] else segments[0]
        val json = String(Base64.getUrlDecoder().decode(padBase64(payloadSegment)))
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
    }

    private fun padBase64(s: String): String {
        val rem = s.length % 4
        return if (rem == 0) s else s + "=".repeat(4 - rem)
    }

    private fun firstNonBlank(claims: Map<String, Any?>, vararg keys: String): String? {
        for (key in keys) {
            val value = claims[key]?.toString()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }
}
