package org.around.ieuri.auth.application.member
import org.around.ieuri.auth.application.member.required.SnsTokenExchangerPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.domain.member.Provider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * authorization code를 폐기용 refresh token으로 교환해 저장한다(SPEC-0012 R5~R7). SNS 가입·로그인이 쓴다.
 * - 교환이 실패해도 예외를 내보내지 않는다(R7). 로그에 code와 토큰 값을 남기지 않는다(POL-013).
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
