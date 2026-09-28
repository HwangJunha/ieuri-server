package org.around.ieuri.auth.infrastructure.adapter.out.client

import com.nimbusds.jwt.JWTClaimsSet
import org.around.ieuri.auth.application.exception.SnsVerificationFailedException
import org.around.ieuri.auth.application.port.out.client.SnsIdentity
import org.around.ieuri.auth.application.port.out.client.SnsIdentityVerifierPort
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.infrastructure.config.SnsProperties
import org.springframework.stereotype.Component

/**
 * SNS 신원 검증기. provider별 JWKS 검증기([JwksIdTokenVerifier])로 id_token의
 * 서명·iss·aud·exp를 검증하고, 검증된 클레임을 [SnsIdentity]로 매핑한다.
 * 모든 환경에서 실검증한다(별도 dev stub 없음 — 로컬에서 SNS를 테스트하려면 실제 provider
 * 로그인으로 발급받은 id_token이 필요하다).
 *
 * - Google: aud=웹 클라이언트 ID, iss=accounts.google.com(두 형태)
 * - Apple:  aud=Bundle ID(kr.ieuri.ktripspot), iss=appleid.apple.com. 이름/이메일은 최초 1회만.
 * - Kakao:  OIDC id_token, aud=REST API 키(앱 ID), iss=kauth.kakao.com
 */
@Component
class CompositeSnsIdentityVerifier(
    properties: SnsProperties,
) : SnsIdentityVerifierPort {

    private val google = JwksIdTokenVerifier(
        jwksUri = properties.google.jwksUri,
        issuers = properties.google.issuers.toSet(),
        audiences = properties.google.clientIds.toSet(),
    )

    private val apple = JwksIdTokenVerifier(
        jwksUri = properties.apple.jwksUri,
        issuers = setOf(properties.apple.issuer),
        audiences = properties.apple.audiences.toSet(),
    )

    private val kakao = JwksIdTokenVerifier(
        jwksUri = properties.kakao.jwksUri,
        issuers = setOf(properties.kakao.issuer),
        audiences = properties.kakao.appKeys.toSet(),
    )

    override fun verify(provider: Provider, idToken: String): SnsIdentity {
        val claims = when (provider) {
            Provider.GOOGLE -> google.verify(idToken)

            Provider.APPLE -> apple.verify(idToken)

            Provider.KAKAO -> kakao.verify(idToken)

            Provider.EMAIL ->
                throw SnsVerificationFailedException("EMAIL은 SNS 검증 대상이 아닙니다.")
        }
        return claims.toSnsIdentity()
    }

    private fun JWTClaimsSet.toSnsIdentity(): SnsIdentity {
        val providerUserId = subject
            ?: throw SnsVerificationFailedException("id_token에 sub가 없습니다.")
        return SnsIdentity(
            providerUserId = providerUserId,
            email = stringOrNull("email"),
            nickname = firstStringOrNull("nickname", "name", "given_name"),
            profileImageUrl = firstStringOrNull("picture", "profile_image_url"),
        )
    }

    private fun JWTClaimsSet.stringOrNull(name: String): String? = getClaim(name)?.toString()?.takeIf { it.isNotBlank() }

    private fun JWTClaimsSet.firstStringOrNull(vararg names: String): String? = names.firstNotNullOfOrNull { stringOrNull(it) }
}
