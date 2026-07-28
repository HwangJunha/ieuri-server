package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * SNS(id_token) 검증 설정. 각 provider의 허용 audience(aud)와 JWKS 엔드포인트를 보관한다.
 * 값은 공개 식별자(클라이언트 ID/앱 키)라 비밀은 아니나 환경별로 분리한다.
 *
 * **aud는 플랫폼별로 다르므로 목록으로 받는다.** 예: Google id_token의 aud는 iOS 앱에선 iOS
 * 클라이언트 ID, Android/Web에선 웹 클라이언트 ID다(SDK 설계상 정상). 하나라도 맞으면 통과.
 *
 * - google.clientIds: 허용할 Google 클라이언트 ID들(웹 + iOS + Android).
 * - apple.audiences:  identityToken의 aud로 허용할 값들(앱 Bundle ID kr.ieuri.ktripspot 등).
 * - kakao.appKeys:    OIDC id_token의 aud로 허용할 카카오 앱 ID(REST API 키)들.
 * jwksUri/issuer는 각 provider 고정값이며 오버라이드 가능하게 설정으로 둔다.
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
