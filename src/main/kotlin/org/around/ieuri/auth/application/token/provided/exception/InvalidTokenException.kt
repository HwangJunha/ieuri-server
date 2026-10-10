package org.around.ieuri.auth.application.token.provided.exception
import org.around.ieuri.common.security.TokenAuthenticationException

/** 토큰 서명/만료/타입이 무효. 최상위 오류 `INVALID_TOKEN` — 클라이언트는 refresh로 재발급을 시도한다. */
class InvalidTokenException(message: String) : TokenAuthenticationException("INVALID_TOKEN", message)
