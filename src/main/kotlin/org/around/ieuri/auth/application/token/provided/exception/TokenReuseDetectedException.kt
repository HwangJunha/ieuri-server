package org.around.ieuri.auth.application.token.provided.exception
import org.around.ieuri.common.security.TokenAuthenticationException

/**
 * 이미 회전·폐기된 refresh 재제출(탈취 의심) → 회원 전체 폐기. 최상위 오류 `TOKEN_REUSE_DETECTED`.
 * - 재발급으로 회복되지 않는다. 클라이언트는 재로그인 화면으로 보내야 한다.
 */
class TokenReuseDetectedException(message: String = "재사용된 리프레시 토큰입니다. 다시 로그인해 주세요.") : TokenAuthenticationException("TOKEN_REUSE_DETECTED", message)
