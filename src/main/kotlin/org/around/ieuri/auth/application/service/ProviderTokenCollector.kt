package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.port.out.client.SnsTokenExchangerPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.domain.vo.Provider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * authorization code를 폐기용 refresh token으로 교환해 저장하는 공통 로직(SPEC-0012 R5·R6·R7).
 * SNS 가입·로그인 양쪽에서 재사용한다.
 *
 * **인증 흐름의 곁가지다** — 교환이 실패해도 예외를 밖으로 내보내지 않는다.
 * provider 장애로 로그인·가입이 막히면 revoke를 위해 가용성을 희생하는 셈이기 때문이다(R7).
 * 실패는 로그로만 남기며 code와 토큰 값은 기록하지 않는다(POL-013).
 */
@Component
class ProviderTokenCollector(
    private val tokenExchanger: SnsTokenExchangerPort,
    private val memberRepository: MemberRepositoryPort,
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
            memberRepository.updateProviderRefreshToken(memberId, refreshToken)
        } catch (ex: Exception) {
            // 실패해도 인증 명령의 결과에 영향을 주지 않는다. code·토큰은 남기지 않는다.
            log.warn("provider 토큰 교환 실패(provider={}, memberId={}): {}", provider, memberId, ex.javaClass.simpleName)
        }
    }
}
