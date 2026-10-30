package org.around.ieuri.auth.application.member.required
import org.around.ieuri.auth.application.member.required.dto.SnsIdentity
import org.around.ieuri.auth.domain.member.Provider

/** SNS id_token을 검증하고 신원을 반환하는 포트. 서명·iss·aud 검증은 provider별 구현이 한다. */
interface SnsIdentityVerifierPort {
    fun verify(provider: Provider, idToken: String): SnsIdentity
}
