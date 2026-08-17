package org.around.ieuri.common.security

/**
 * 인증/인가 실패 예외(공유 커널). HTTP 인증 계층의 일반 개념이라 특정 도메인에 속하지 않는다.
 * 리졸버에서 잡지 않으면 GraphQL 최상위 error로 전파된다.
 */

/** 인증이 필요한데 유효한 accessToken이 없을 때. */
class UnauthorizedException(message: String) : RuntimeException(message)

/** 인증은 됐으나 요구되는 권한(permission)이 없을 때. */
class ForbiddenException(message: String) : RuntimeException(message)

/**
 * 토큰 자체가 인증에 실패했을 때(서명·만료·타입 불일치, 재사용 감지). 코드를 함께 싣는다.
 *
 * **`UserFacingException`이 아니다** — payload의 `userErrors`가 아니라 최상위 오류로 나가야
 * 하는 인증 실패다(POL-016). 다만 `UNAUTHENTICATED`와 뭉뚱그리지 않고 코드를 나눈다:
 * 만료는 재발급으로 회복되지만 재사용 감지는 재로그인을 요구하므로, 클라이언트의 분기가 다르다.
 *
 * 공유 커널에 두는 이유는 [SecurityGraphqlExceptionHandler]가 코드를 실어 내보내야 하는데
 * common이 auth의 예외 타입을 알 수 없기 때문이다 — auth가 이 타입을 상속해 던진다.
 */
abstract class TokenAuthenticationException(val code: String, message: String) : RuntimeException(message)
