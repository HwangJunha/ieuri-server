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
 * 인증은 됐으나 이메일 인증을 마치지 않은 회원이 허용 목록 밖의 오퍼레이션을 호출할 때
 * (SPEC-0024 R9).
 *
 * **`UserFacingException`이 아니라 최상위 오류다**(POL-016). 허용 목록 7개를 뺀 모든
 * 오퍼레이션에 걸리므로 payload마다 `userErrors` 코드를 다는 대안은 20여 개 타입을 전부
 * 건드리게 된다. 코드 문자열은 `EMAIL_NOT_VERIFIED` 그대로이고 나타나는 위치만 바뀌었다.
 */
class EmailNotVerifiedException(message: String) : RuntimeException(message)
