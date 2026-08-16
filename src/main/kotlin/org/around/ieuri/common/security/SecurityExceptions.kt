package org.around.ieuri.common.security

/**
 * 인증/인가 실패 예외(공유 커널). HTTP 인증 계층의 일반 개념이라 특정 도메인에 속하지 않는다.
 * 리졸버에서 잡지 않으면 GraphQL 최상위 error로 전파된다.
 */

/** 인증이 필요한데 유효한 accessToken이 없을 때. */
class UnauthorizedException(message: String) : RuntimeException(message)

/** 인증은 됐으나 요구되는 권한(permission)이 없을 때. */
class ForbiddenException(message: String) : RuntimeException(message)
