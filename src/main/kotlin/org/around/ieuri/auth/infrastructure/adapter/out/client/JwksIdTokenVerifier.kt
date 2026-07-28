package org.around.ieuri.auth.infrastructure.adapter.out.client

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.around.ieuri.auth.application.exception.SnsVerificationFailedException
import java.net.URI

/**
 * provider 공개키(JWKS)로 OIDC id_token의 서명·발급자(iss)·audience(aud)·만료(exp)를 검증하고
 * 검증된 클레임을 돌려주는 재사용 컴포넌트. Nimbus JOSE로 JWKS를 원격 조회·캐시한다.
 *
 * 어떤 사유로 실패했는지 과도하게 노출하지 않도록, 모든 검증 실패는 단일
 * [SnsVerificationFailedException]으로 일반화한다.
 *
 * @param jwksUri  provider의 JWKS 엔드포인트
 * @param issuers  허용 발급자(iss) 집합(구글은 두 형태를 허용하므로 집합)
 * @param audience 이 서버가 기대하는 aud(웹 클라이언트 ID / Bundle ID / 앱 키)
 * @param algorithms 허용 서명 알고리즘(RS256/ES256 등)
 */
class JwksIdTokenVerifier(
    jwkSource: JWKSource<SecurityContext>,
    private val issuers: Set<String>,
    audience: String,
    algorithms: Set<JWSAlgorithm> = setOf(JWSAlgorithm.RS256, JWSAlgorithm.ES256),
) {
    /** 운영 경로: JWKS URI를 원격 조회·캐시하는 소스로 구성한다. */
    constructor(
        jwksUri: String,
        issuers: Set<String>,
        audience: String,
        algorithms: Set<JWSAlgorithm> = setOf(JWSAlgorithm.RS256, JWSAlgorithm.ES256),
    ) : this(
        JWKSourceBuilder.create<SecurityContext>(URI.create(jwksUri).toURL()).build(),
        issuers,
        audience,
        algorithms,
    )

    private val processor: ConfigurableJWTProcessor<SecurityContext> =
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(algorithms, jwkSource)
            // aud + exp는 표준 검증기로, iss는 아래에서 별도로(구글의 두 iss 형태 허용) 확인한다.
            jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
                audience,
                null,
                setOf("sub", "iss", "exp"),
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
