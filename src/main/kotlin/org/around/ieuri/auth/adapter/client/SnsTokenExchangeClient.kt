package org.around.ieuri.auth.adapter.client
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.around.ieuri.auth.adapter.config.SnsRevokeProperties
import org.around.ieuri.auth.application.member.required.SnsTokenExchangerPort
import org.around.ieuri.auth.domain.member.Provider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.time.Clock

/**
 * authorization code → refresh token 교환 클라이언트(SPEC-0012 R5). Kakao는 교환하지 않는다.
 * - 자격증명 미설정이거나 응답에 refresh_token이 없으면 null이다.
 */
@Component
class SnsTokenExchangeClient(
    // 이름만으로 해석하면 tourApiRestClient가 조용히 주입될 수 있어 명시적으로 지정한다.
    @Qualifier("snsRevokeRestClient") private val snsRevokeRestClient: RestClient,
    private val properties: SnsRevokeProperties,
    private val appleClientSecretFactory: AppleClientSecretFactory,
    private val clock: Clock,
) : SnsTokenExchangerPort {

    private val log = LoggerFactory.getLogger(SnsTokenExchangeClient::class.java)

    override fun exchangeForRefreshToken(provider: Provider, authorizationCode: String): String? = when (provider) {
        Provider.APPLE -> exchangeApple(authorizationCode)

        Provider.GOOGLE -> exchangeGoogle(authorizationCode)

        // 어드민 키 + 회원번호로 해제하므로 토큰이 필요 없다.
        Provider.KAKAO -> null

        Provider.EMAIL -> null
    }

    private fun exchangeApple(authorizationCode: String): String? {
        val apple = properties.apple
        if (!apple.isConfigured()) {
            log.warn("Apple revoke 자격증명이 설정되지 않아 토큰 교환을 건너뜁니다.")
            return null
        }
        return postForRefreshToken(
            APPLE_TOKEN_URI,
            mapOf(
                "client_id" to apple.clientId,
                "client_secret" to appleClientSecretFactory.create(clock.instant()),
                "code" to authorizationCode,
                "grant_type" to "authorization_code",
            ),
        )
    }

    private fun exchangeGoogle(authorizationCode: String): String? {
        val google = properties.google
        if (!google.isConfigured()) {
            log.warn("Google revoke 자격증명이 설정되지 않아 토큰 교환을 건너뜁니다.")
            return null
        }
        return postForRefreshToken(
            GOOGLE_TOKEN_URI,
            mapOf(
                "client_id" to google.clientId,
                "client_secret" to google.clientSecret,
                "code" to authorizationCode,
                "grant_type" to "authorization_code",
            ),
        )
    }

    private fun postForRefreshToken(uri: String, form: Map<String, String>): String? {
        val body = LinkedMultiValueMap<String, String>().apply { form.forEach { (k, v) -> add(k, v) } }
        val response = snsRevokeRestClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .body(TokenResponse::class.java)
        return response?.refreshToken?.takeIf { it.isNotBlank() }
    }

    /** 교환 응답에서 refresh_token만 쓴다. 나머지 필드는 무시한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TokenResponse(
        @param:JsonProperty("refresh_token") val refreshToken: String? = null,
    )

    private companion object {
        const val APPLE_TOKEN_URI = "https://appleid.apple.com/auth/token"
        const val GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token"
    }
}
