package org.around.ieuri.auth.domain.shared

/**
 * 사용자에게 보여줄 수 있는 예상 실패. 리졸버가 최상위 error가 아니라 payload의 `userErrors`로 바꾼다.
 * - 토큰 실패(InvalidToken/TokenReuse 등)는 이 타입이 아니며 최상위 error로 전파된다.
 */
abstract class AuthUserException(val code: String, message: String) : RuntimeException(message)
