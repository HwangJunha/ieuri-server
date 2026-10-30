package org.around.ieuri.auth.adapter.client
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.around.ieuri.auth.application.member.provided.exception.SnsVerificationFailedException
import java.net.URI

/**
 * provider JWKS로 OIDC id_token의 서명·iss·aud·exp를 검증하고 클레임을 돌려준다(Nimbus, JWKS 원격 조회·캐시).
 * - 모든 실패는 단일 [SnsVerificationFailedException]으로 일반화한다.
 * @param audiences 허용 aud 집합. 한 provider도 플랫폼별로 aud가 달라(예: Google iOS/웹) 하나라도 맞으면 통과한다.
 */
class JwksIdTokenVerifier(
    jwkSource: JWKSource<SecurityContext>,
    private val issuers: Set<String>,
    audiences: Set<String>,
    algorithms: Set<JWSAlgorithm> = setOf(JWSAlgorithm.RS256, JWSAlgorithm.ES256),
) {
    /** 운영 경로: JWKS URI를 원격 조회·캐시하는 소스로 구성한다. */
    constructor(
        jwksUri: String,
        issuers: Set<String>,
        audiences: Set<String>,
        algorithms: Set<JWSAlgorithm> = setOf(JWSAlgorithm.RS256, JWSAlgorithm.ES256),
    ) : this(
        JWKSourceBuilder.create<SecurityContext>(URI.create(jwksUri).toURL()).build(),
        issuers,
        audiences,
        algorithms,
    )

    private val processor: ConfigurableJWTProcessor<SecurityContext> =
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(algorithms, jwkSource)
            // aud(집합 중 하나라도 일치)+exp는 표준 검증기로, iss는 아래에서 별도로 확인한다.
            // 4-인자 형태: (허용 aud 집합, 정확일치 클레임, 필수 클레임, 금지 클레임).
            jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
                audiences,
                null,
                setOf("sub", "iss", "exp"),
                emptySet(),
            )
        }

    /** 검증에 성공하면 클레임을, 실패하면 [SnsVerificationFailedException]을 던진다. */
    fun verify(idToken: String): JWTClaimsSet {
        val claims = try {
            processor.process(idToken, null)
        } catch (e: Exception) {
            throw SnsVerificationFailedException("SNS id_token 검증에 실패했습니다.")
        }
        if (claims.issuer !in issuers) {
            throw SnsVerificationFailedException("SNS id_token 검증에 실패했습니다.")
        }
        return claims
    }
}
