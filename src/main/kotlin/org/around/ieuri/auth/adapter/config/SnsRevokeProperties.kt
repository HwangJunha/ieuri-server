package org.around.ieuri.auth.adapter.config
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.Resource
import java.time.Duration

/**
 * provider 연결 해제용 자격증명(SPEC-0012). 로그에 섞여 찍히지 않도록 SnsProperties와 분리한다.
 * - 값이 비면 해제를 시도하지 않고 실패로 기록한다(탈퇴는 진행). timeout은 외부 호출 대기 상한이다(R11).
 */
@ConfigurationProperties(prefix = "ieuri.sns-revoke")
data class SnsRevokeProperties(
    val apple: Apple = Apple(),
    val google: Google = Google(),
    val kakao: Kakao = Kakao(),
    val timeout: Duration = Duration.ofSeconds(3),
) {
    /** client secret을 ES256 JWT로 서명한다. 개인키는 파일 경로로만 받는다(`file:`/`classpath:`) — 환경변수는 덤프에 노출된다. */
    data class Apple(
        val teamId: String = "",
        val keyId: String = "",
        val clientId: String = "",
        val privateKeyLocation: Resource? = null,
    ) {
        fun isConfigured(): Boolean = teamId.isNotBlank() && keyId.isNotBlank() && clientId.isNotBlank() &&
            privateKeyLocation?.exists() == true
    }

    /** client secret 문자열을 그대로 쓴다. */
    data class Google(
        val clientId: String = "",
        val clientSecret: String = "",
    ) {
        fun isConfigured(): Boolean = clientId.isNotBlank() && clientSecret.isNotBlank()
    }

    /** 어드민 키 + 회원번호로 해제하므로 토큰 교환이 필요 없다. */
    data class Kakao(
        val adminKey: String = "",
    ) {
        fun isConfigured(): Boolean = adminKey.isNotBlank()
    }
}
