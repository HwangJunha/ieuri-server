package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.MemberCommandUseCase
import org.around.ieuri.auth.application.member.provided.MemberLoginUseCase
import org.around.ieuri.auth.application.member.provided.exception.InvalidCredentialsException
import org.around.ieuri.auth.application.member.required.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.member.required.PasswordEncoderPort
import org.around.ieuri.auth.application.member.required.SnsIdentityVerifierPort
import org.around.ieuri.auth.domain.member.Email
import org.around.ieuri.auth.domain.member.Provider
import org.springframework.stereotype.Service

/**
 * 회원 인증. 트랜잭션을 갖지 않는다 — id_token 검증·토큰 교환(HTTP)과 비밀번호 대조(bcrypt)를 DB 단계 밖에 둔다.
 * - 이메일 미인증은 여기서 막지 않는다(SPEC-0024 R8). 차단은 오퍼레이션 게이트가 맡는다(R9).
 */
@Service
class MemberLoginService(
    private val snsVerifier: SnsIdentityVerifierPort,
    private val members: MemberCommandUseCase,
    private val providerTokenCollector: ProviderTokenCollector,
    private val passwordEncoder: PasswordEncoderPort,
    private val identifierProtector: LoginIdentifierProtectorPort,
) : MemberLoginUseCase {

    override fun loginBySns(provider: Provider, idToken: String, authorizationCode: String?): Long {
        val identity = snsVerifier.verify(provider, idToken)
        val memberId = members.admitSns(provider, identity.providerUserId)
        // 탈퇴용 토큰 보충. 실패해도 로그인은 성공한다(R7).
        providerTokenCollector.collect(memberId, provider, authorizationCode)
        return memberId
    }

    override fun loginByEmail(email: String, password: String): Long {
        val credential = members.findEmailCredential(identifierProtector.protect(Email(email).value))
            ?: throw invalidCredentials()
        // 해시가 없으면 SNS 계정이다 — 이메일 로그인은 안 되고, 존재 여부도 드러내지 않는다.
        val passwordHash = credential.passwordHash ?: throw invalidCredentials()
        if (!passwordEncoder.matches(password, passwordHash)) throw invalidCredentials()

        members.admit(credential.memberId)
        return credential.memberId
    }

    private fun invalidCredentials() = InvalidCredentialsException()
}
