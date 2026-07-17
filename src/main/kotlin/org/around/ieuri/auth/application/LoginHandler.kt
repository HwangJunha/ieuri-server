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
import java.time.Clock

/**
 * 로그인 오케스트레이션. SNS 인증 → 기존 회원 조회 → 상태 확인 → 토큰 발급.
 * 미가입 회원은 거부(회원가입으로 유도). 정지 회원은 거부하되,
 * **기간제 정지가 만료됐으면 자동 복구** 후 로그인을 허용한다.
 */
@Service
class LoginHandler(
    private val snsVerifier: SnsIdentityVerifierPort,
    private val memberRepository: MemberRepositoryPort,
    private val tokenIssuer: TokenIssuer,
    private val clock: Clock,
) : LoginUseCase {

    @Transactional
    override fun handle(command: LoginCommand): TokenPairResult {
        val now = clock.instant()
        val identity = snsVerifier.verify(command.provider, command.idToken)
        val providerUserId = ProviderUserId(identity.providerUserId)

        val authentication = memberRepository.findAuthentication(command.provider, providerUserId)
            ?: throw MemberNotFoundException("가입되지 않은 계정입니다. 회원가입이 필요합니다.")

        when (authentication.status) {
            MemberStatus.ACTIVE -> Unit
            MemberStatus.SUSPENDED -> {
                val until = authentication.suspendedUntil
                if (until != null && !now.isBefore(until)) {
                    // 기간제 정지 만료 → 자동 복구 후 로그인 허용.
                    val member = memberRepository.load(authentication.memberId)
                        ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
                    member.reactivate(now)
                    memberRepository.updateStatus(member)
                } else {
                    val reason = authentication.suspensionReason?.let { " 사유: $it" } ?: ""
                    throw InactiveMemberException("정지된 계정입니다.$reason")
                }
            }
        }

        return tokenIssuer.issueFor(authentication.memberId)
    }
}
