package org.around.ieuri.auth.infrastructure.adapter.out.client

import org.around.ieuri.auth.application.member.required.SnsTokenRevokerPort
import org.around.ieuri.auth.application.member.required.dto.RevokeSnsConnectionCommand
import org.around.ieuri.auth.application.member.required.dto.SnsRevokeResult
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.infrastructure.config.SnsRevokeProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.time.Clock

/**
 * 탈퇴 시 provider 계정 연결 해제 클라이언트(SPEC-0012 R8~R10). Apple·Google은 토큰 폐기, Kakao는 어드민 키 + 회원번호.
 * - 예외를 밖으로 던지지 않는다. 호출자가 @Transactional 안이라 예외가 나가면 회원 삭제까지 롤백된다(R11).
 * - 대기 상한은 snsRevokeRestClient의 타임아웃이다.
 */
@Component
class SnsConnectionRevokeClient(
    // 이름만으로 해석하면 tourApiRestClient가 조용히 주입될 수 있어 명시적으로 지정한다.
    @Qualifier("snsRevokeRestClient") private val snsRevokeRestClient: RestClient,
    private val properties: SnsRevokeProperties,
    private val appleClientSecretFactory: AppleClientSecretFactory,
    private val clock: Clock,
) : SnsTokenRevokerPort {

    private val log = LoggerFactory.getLogger(SnsConnectionRevokeClient::class.java)

    override fun revoke(command: RevokeSnsConnectionCommand): SnsRevokeResult = try {
        when (command.provider) {
            Provider.APPLE -> revokeApple(command.providerRefreshToken)

            Provider.GOOGLE -> revokeGoogle(command.providerRefreshToken)

            Provider.KAKAO -> unlinkKakao(command.providerUserId)

            // 자체 가입 회원은 연결된 provider가 없다.
            Provider.EMAIL -> SnsRevokeResult.Skipped
        }
    } catch (ex: Exception) {
        // 토큰·이메일은 남기지 않는다(R11). 예외 타입만 사유로 남긴다.
        log.warn("provider 연결 해제 실패(provider={}): {}", command.provider, ex.javaClass.simpleName)
        SnsRevokeResult.Failed(ex.javaClass.simpleName)
    }

    private fun revokeApple(refreshToken: String?): SnsRevokeResult {
        val apple = properties.apple
        if (!apple.isConfigured()) return SnsRevokeResult.Failed("APPLE_CREDENTIALS_MISSING")
        // 클라이언트 업데이트 전에 로그인한 회원은 저장된 토큰이 없다 — 시도하지 않는다(R8).
        val token = refreshToken ?: return SnsRevokeResult.Skipped

        postForm(
            APPLE_REVOKE_URI,
            mapOf(
                "client_id" to apple.clientId,
                "client_secret" to appleClientSecretFactory.create(clock.instant()),
                "token" to token,
                "token_type_hint" to "refresh_token",
            ),
        )
        return SnsRevokeResult.Revoked
    }

    private fun revokeGoogle(refreshToken: String?): SnsRevokeResult {
        val token = refreshToken ?: return SnsRevokeResult.Skipped

        postForm(GOOGLE_REVOKE_URI, mapOf("token" to token))
        return SnsRevokeResult.Revoked
    }

    /** Kakao는 토큰 없이 어드민 키 + 회원번호(providerUserId)로 해제한다. */
    private fun unlinkKakao(providerUserId: String): SnsRevokeResult {
        val kakao = properties.kakao
        if (!kakao.isConfigured()) return SnsRevokeResult.Failed("KAKAO_ADMIN_KEY_MISSING")

        val body = LinkedMultiValueMap<String, String>().apply {
            add("target_id_type", "user_id")
            add("target_id", providerUserId)
        }
        snsRevokeRestClient.post()
            .uri(KAKAO_UNLINK_URI)
            .header("Authorization", "KakaoAK ${kakao.adminKey}")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .toBodilessEntity()
        return SnsRevokeResult.Revoked
    }

    private fun postForm(uri: String, form: Map<String, String>) {
        val body = LinkedMultiValueMap<String, String>().apply { form.forEach { (k, v) -> add(k, v) } }
        snsRevokeRestClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .toBodilessEntity()
    }

    private companion object {
        const val APPLE_REVOKE_URI = "https://appleid.apple.com/auth/revoke"
        const val GOOGLE_REVOKE_URI = "https://oauth2.googleapis.com/revoke"
        const val KAKAO_UNLINK_URI = "https://kapi.kakao.com/v1/user/unlink"
    }
}
