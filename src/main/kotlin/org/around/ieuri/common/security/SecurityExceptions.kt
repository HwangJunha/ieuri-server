package org.around.ieuri.common.security

/*
 * 인증/인가 실패 예외(공유 커널). HTTP 인증 계층의 일반 개념이라 특정 도메인에 속하지 않는다.
 * 리졸버에서 잡지 않으면 GraphQL 최상위 error로 전파된다.
 */

/** 인증이 필요한데 유효한 accessToken이 없을 때. */
class UnauthorizedException(message: String) : RuntimeException(message)

/**
 * 토큰이 제시됐으나 거절되어, 신원 없이도 동작하는 연산이 이를 비로그인으로 다루기를
 * 거부할 때(SPEC-0032 R1). 최상위 오류 `UNAUTHENTICATED_TOKEN_REJECTED`로 나간다.
 *
 * **`UNAUTHENTICATED`와 코드를 나누는 이유**: 저쪽은 "로그인이 필요하다"라서 로그인 화면이
 * 정답이지만, 이쪽은 **이미 로그인했던 세션이 만료된 것**이라 갱신이 정답이다. 뭉뚱그리면
 * 갱신으로 회복될 사용자를 로그인 화면으로 보내게 된다.
 */
class StaleTokenException(message: String) : TokenAuthenticationException("UNAUTHENTICATED_TOKEN_REJECTED", message)

/**
 * 소켓 신원이 더 이상 유효하지 않아 새 구독을 열 수 없을 때(SPEC-0032 R7).
 * 최상위 오류 `SUBSCRIPTION_UNAUTHENTICATED`로 나간다.
 *
 * **`UNAUTHENTICATED`와 코드를 나누는 이유는 클라이언트의 행동이 다르기 때문이다** —
 * 소켓 신원은 `connection_init`에 고정되어 있어 **같은 토큰으로 다시 붙으면 결과가 같다.**
 * 재연결이 아니라 **갱신 후 재구독**이어야 하며, 이 구분이 없어서 무한 재연결이 발생했다.
 */
class SubscriptionUnauthenticatedException(message: String) : TokenAuthenticationException("SUBSCRIPTION_UNAUTHENTICATED", message)

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
