package org.around.ieuri.auth.application

import org.around.ieuri.auth.application.command.LoginCommand
import org.around.ieuri.auth.application.exception.InactiveMemberException
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.`in`.command.LoginUseCase
import org.around.ieuri.auth.application.port.`in`.command.TokenPairResult
import org.around.ieuri.auth.application.port.out.client.SnsIdentityVerifierPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.ProviderUserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 로그인 오케스트레이션. SNS 인증 → 기존 회원 조회 → 상태 확인 → 토큰 발급.
 * 미가입 회원은 거부(회원가입으로 유도), ACTIVE 아닌 회원도 거부한다.
 */
@Service
class LoginHandler(
    private val snsVerifier: SnsIdentityVerifierPort,
    private val memberRepository: MemberRepositoryPort,
    private val tokenIssuer: TokenIssuer,
) : LoginUseCase {

    @Transactional(readOnly = true)
    override fun handle(command: LoginCommand): TokenPairResult {
        val identity = snsVerifier.verify(command.provider, command.idToken)
        val providerUserId = ProviderUserId(identity.providerUserId)

        val authentication = memberRepository.findAuthentication(command.provider, providerUserId)
            ?: throw MemberNotFoundException("가입되지 않은 계정입니다. 회원가입이 필요합니다.")

        if (authentication.status != MemberStatus.ACTIVE) {
            throw InactiveMemberException("로그인할 수 없는 회원 상태입니다.")
        }

        return tokenIssuer.issueFor(authentication.memberId)
    }
}
