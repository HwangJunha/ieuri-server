package org.around.ieuri.common.security

/*
 * 인증/인가 실패 예외(공유 커널). 리졸버에서 잡지 않으면 GraphQL 최상위 error로 전파된다.
 */

/** 인증이 필요한데 유효한 accessToken이 없을 때. */
class UnauthorizedException(message: String) : RuntimeException(message)

/**
 * 토큰이 제시됐으나 거절되어, 신원 없이도 동작하는 연산이 비로그인 처리를 거부할 때(SPEC-0032 R1).
 * - `UNAUTHENTICATED`(로그인 필요)와 달리 클라이언트는 갱신으로 회복한다.
 */
class StaleTokenException(message: String) : TokenAuthenticationException("UNAUTHENTICATED_TOKEN_REJECTED", message)

/**
 * 소켓 신원이 더 이상 유효하지 않아 새 구독을 열 수 없을 때(SPEC-0032 R7).
 * - 같은 토큰으로 재연결하면 결과가 같다 — 클라이언트는 갱신 후 재구독해야 한다.
 */
class SubscriptionUnauthenticatedException(message: String) : TokenAuthenticationException("SUBSCRIPTION_UNAUTHENTICATED", message)

/** 인증은 됐으나 요구되는 권한(permission)이 없을 때. */
class ForbiddenException(message: String) : RuntimeException(message)

/**
 * 토큰 자체가 인증에 실패했을 때(서명·만료·타입 불일치, 재사용 감지). 코드를 함께 싣는다.
 * - `userErrors`가 아니라 최상위 오류로 나간다(POL-016). auth가 이 타입을 상속해 던진다.
 */
abstract class TokenAuthenticationException(val code: String, message: String) : RuntimeException(message)
