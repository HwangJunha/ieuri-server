package org.around.ieuri.auth.application.token.provided.exception
import org.around.ieuri.common.security.TokenAuthenticationException

/**
 * 토큰 저장소(Valkey)에 닿지 못해 판정 불가. 최상위 오류 `AUTH_STORE_UNAVAILABLE`(SPEC-0032 R6).
 * - fail-closed: 요청은 실패한다. 통과시키면 재사용 감지가 무력화된다.
 * - 토큰은 멀쩡하므로 클라이언트는 재로그인이 아니라 잠시 후 같은 요청을 재시도한다.
 */
class AuthStoreUnavailableException(message: String, cause: Throwable? = null) : TokenAuthenticationException("AUTH_STORE_UNAVAILABLE", message) {
    init {
        cause?.let { initCause(it) }
    }
}
