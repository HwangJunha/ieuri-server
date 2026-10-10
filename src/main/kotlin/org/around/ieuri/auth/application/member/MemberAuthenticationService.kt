package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.MemberAuthenticationUseCase
import org.around.ieuri.auth.application.member.provided.dto.EmailCredentialView
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.auth.domain.member.ProviderUserId
import org.around.ieuri.common.persistence.ExposedConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** 로그인의 DB 단계. 외부 호출(id_token 검증·토큰 교환·비밀번호 대조)은 [MemberLoginService]가 이 밖에서 한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class MemberAuthenticationService(
    private val memberRepository: MemberRepository,
    private val clock: Clock,
) : MemberAuthenticationUseCase {

    override fun admitSns(provider: Provider, providerUserId: String): Long {
        val member = memberRepository.findByProviderUserId(provider, ProviderUserId(providerUserId))
            ?: throw MemberNotFoundException("가입되지 않은 계정입니다. 회원가입이 필요합니다.")
        return admit(member)
    }

    override fun findEmailCredential(loginIdentifier: String): EmailCredentialView? = memberRepository.findByProviderUserId(Provider.EMAIL, ProviderUserId(loginIdentifier))
        ?.let { EmailCredentialView(memberId = requireNotNull(it.id), passwordHash = it.passwordHash?.value) }

    override fun admit(memberId: Long) {
        admit(memberRepository.findById(memberId) ?: throw MemberNotFoundException("회원을 찾을 수 없습니다."))
    }

    override fun keepProviderRefreshToken(memberId: Long, token: String) {
        memberRepository.findById(memberId)?.keepProviderRefreshToken(token)?.let(memberRepository::saveProviderRefreshToken)
    }

    /** 기간제 정지가 만료돼 복구됐을 때만 상태를 쓴다. */
    private fun admit(member: Member): Long {
        val admitted = member.admit(clock.instant())
        if (admitted != member) memberRepository.saveStatus(admitted)
        return requireNotNull(member.id)
    }
}
