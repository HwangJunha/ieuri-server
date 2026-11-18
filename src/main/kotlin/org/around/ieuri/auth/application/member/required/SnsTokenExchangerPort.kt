package org.around.ieuri.auth.application.member.required
import org.around.ieuri.auth.domain.member.Provider

/**
 * authorization code를 폐기용 refresh token으로 교환하는 포트(SPEC-0012 R5).
 * - 교환 실패는 예외가 아니라 null이다 — 로그인·가입을 막지 않는다(R7).
 * - Kakao는 어드민 키로 해제하므로 교환하지 않고 null을 반환한다.
 */
interface SnsTokenExchangerPort {
    fun exchangeForRefreshToken(provider: Provider, authorizationCode: String): String?
}
