package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * SNS(id_token) 검증 설정. 각 provider의 audience(aud)와 JWKS 엔드포인트를 보관한다.
 * 값은 공개 식별자(클라이언트 ID/앱 키)라 비밀은 아니나 환경별로 분리한다.
 *
 * - google.clientId: id_token의 `aud`로 검증할 웹(server) OAuth 클라이언트 ID.
 * - apple.audience:  identityToken의 `aud`로 검증할 앱 Bundle ID(kr.ieuri.ktripspot).
 * - kakao.appKey:    OIDC id_token의 `aud`로 검증할 카카오 REST API 키(앱 ID).
 * jwksUri/issuer는 각 provider 고정값이며 오버라이드 가능하게 설정으로 둔다.
 */
@ConfigurationProperties(prefix = "ieuri.sns")
data class SnsProperties(
    val google: Google,
    val apple: Apple,
    val kakao: Kakao,
) {
    data class Google(
        val clientId: String,
        val jwksUri: String,
        val issuers: List<String>,
    )

    data class Apple(
        val audience: String,
        val jwksUri: String,
        val issuer: String,
    )

    data class Kakao(
        val appKey: String,
        val jwksUri: String,
        val issuer: String,
    )
}
