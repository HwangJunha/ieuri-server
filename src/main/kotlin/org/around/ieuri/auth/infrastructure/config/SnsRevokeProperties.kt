package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.Resource
import java.time.Duration

/**
 * provider 연결 해제용 자격증명(SPEC-0012). 공개 식별자만 담는 SnsProperties와 **일부러 분리한다** —
 * 한 클래스에 섞으면 실수로 로그에 찍기 쉽다.
 *
 * 값이 비어 있으면 해당 provider 해제는 시도하지 않고 실패로 기록된다(탈퇴 자체는 진행된다).
 * timeout은 외부 호출이 트랜잭션을 길게 잡지 않도록 하는 대기 상한이다(R11).
 */
@ConfigurationProperties(prefix = "ieuri.sns-revoke")
data class SnsRevokeProperties(
    val apple: Apple = Apple(),
    val google: Google = Google(),
    val kakao: Kakao = Kakao(),
    val timeout: Duration = Duration.ofSeconds(3),
) {
    /**
     * client secret을 ES256 JWT로 직접 서명해야 한다.
     *
     * 개인키는 **파일 경로로만** 받는다(`file:`/`classpath:` 접두사를 Spring이 해석한다).
     * 여러 줄 PEM을 환경변수에 넣으려면 개행을 이스케이프해야 해서 깨지기 쉽고,
     * 환경변수는 `/proc/<pid>/environ`과 크래시 덤프에 노출된다.
     * k8s/k3s에서는 Secret을 볼륨으로 마운트하고 그 경로를 주면 된다.
     */
    data class Apple(
        val teamId: String = "",
        val keyId: String = "",
        val clientId: String = "",
        val privateKeyLocation: Resource? = null,
    ) {
        fun isConfigured(): Boolean =
            teamId.isNotBlank() && keyId.isNotBlank() && clientId.isNotBlank() &&
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
