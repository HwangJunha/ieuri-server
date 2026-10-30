package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.MemberCommandUseCase
import org.around.ieuri.auth.application.member.provided.SnsRegistrationUseCase
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberResult
import org.around.ieuri.auth.application.member.provided.dto.RegisterSnsIdentityCommand
import org.around.ieuri.auth.application.member.required.SnsIdentityVerifierPort
import org.springframework.stereotype.Service

/**
 * SNS 회원가입. 트랜잭션을 갖지 않는다 — id_token 검증과 provider 토큰 교환(HTTP)을 가입 트랜잭션 밖에 둔다.
 * - 토큰 확보는 가입이 커밋된 뒤다. 실패해도 가입은 성공한다(R7) — 다음 로그인이 다시 받는다.
 */
@Service
class SnsRegistrationService(
    private val snsVerifier: SnsIdentityVerifierPort,
    private val members: MemberCommandUseCase,
    private val providerTokenCollector: ProviderTokenCollector,
) : SnsRegistrationUseCase {

    override fun registerBySns(command: RegisterMemberCommand): RegisterMemberResult {
        val identity = snsVerifier.verify(command.provider, command.idToken)
        val registered = members.registerBySnsIdentity(
            RegisterSnsIdentityCommand(
                request = command,
                providerUserId = identity.providerUserId,
                email = identity.email,
                nickname = identity.nickname,
                profileImageUrl = identity.profileImageUrl,
            ),
        )
        providerTokenCollector.collect(registered.memberId, command.provider, command.authorizationCode)
        return registered.result
    }
}
