package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * SNS id_token 검증 설정(provider별 허용 aud·JWKS). 비밀은 아니나 환경별로 분리한다.
 * - aud는 플랫폼별로 달라 목록으로 받는다(google.clientIds, apple.audiences, kakao.appKeys).
 */
@ConfigurationProperties(prefix = "ieuri.sns")
data class SnsProperties(
    val google: Google,
    val apple: Apple,
    val kakao: Kakao,
) {
    data class Google(
        val clientIds: List<String>,
        val jwksUri: String,
        val issuers: List<String>,
    )

    data class Apple(
        val audiences: List<String>,
        val jwksUri: String,
        val issuer: String,
    )

    data class Kakao(
        val appKeys: List<String>,
        val jwksUri: String,
        val issuer: String,
    )
}
