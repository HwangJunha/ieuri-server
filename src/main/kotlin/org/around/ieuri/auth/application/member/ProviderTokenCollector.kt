package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.MemberAuthenticationUseCase
import org.around.ieuri.auth.application.member.required.SnsTokenExchangerPort
import org.around.ieuri.auth.domain.member.Provider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * authorization code를 탈퇴용 refresh token으로 교환해 보관한다(SPEC-0012 R5~R7). SNS 가입·로그인이 쓴다.
 * - 회원 트랜잭션 밖에서 부른다. 교환(HTTP) 뒤 저장만 한 트랜잭션이다.
 * - 실패해도 예외를 내보내지 않는다(R7). 로그에 code와 토큰 값을 남기지 않는다(POL-013).
 */
@Component
class ProviderTokenCollector(
    private val tokenExchanger: SnsTokenExchangerPort,
    private val authentication: MemberAuthenticationUseCase,
) {
    private val log = LoggerFactory.getLogger(ProviderTokenCollector::class.java)

    fun collect(memberId: Long, provider: Provider, authorizationCode: String?) {
        // EMAIL 회원은 교환 대상이 아니고(R5), code가 없으면 보충할 것이 없다.
        if (provider == Provider.EMAIL) return
        val code = authorizationCode?.takeIf { it.isNotBlank() } ?: return

        try {
            val refreshToken = tokenExchanger.exchangeForRefreshToken(provider, code)
            if (refreshToken == null) {
                log.info("provider 토큰 교환 대상 아님/미발급(provider={}, memberId={})", provider, memberId)
                return
            }
            authentication.keepProviderRefreshToken(memberId, refreshToken)
        } catch (ex: Exception) {
            log.warn("provider 토큰 교환 실패(provider={}, memberId={}): {}", provider, memberId, ex.javaClass.simpleName)
        }
    }
}
