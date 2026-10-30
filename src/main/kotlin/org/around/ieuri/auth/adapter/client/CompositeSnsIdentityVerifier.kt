package org.around.ieuri.auth.adapter.client
import com.nimbusds.jwt.JWTClaimsSet
import org.around.ieuri.auth.adapter.config.SnsProperties
import org.around.ieuri.auth.application.member.provided.exception.SnsVerificationFailedException
import org.around.ieuri.auth.application.member.required.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.member.required.dto.SnsIdentity
import org.around.ieuri.auth.domain.member.Provider
import org.springframework.stereotype.Component

/**
 * SNS 신원 검증기. provider별 [JwksIdTokenVerifier]로 id_token을 검증하고 [SnsIdentity]로 매핑한다.
 * - 모든 환경에서 실검증한다(dev stub 없음). Apple은 이름/이메일을 최초 1회만 준다.
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
