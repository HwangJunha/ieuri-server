package org.around.ieuri.auth.application.exception

/** 이미 같은 (provider, provider_user_id)로 가입된 회원이 있을 때. */
class MemberAlreadyRegisteredException(message: String) : RuntimeException(message)

/** 검증 핸들(publicId)에 해당하는 챌린지를 찾을 수 없을 때. */
class VerificationNotFoundException(message: String) : RuntimeException(message)

/** 재발송 쿨다운 이내 재요청 등 rate limit 위반. */
class VerificationRateLimitedException(message: String) : RuntimeException(message)

/** 로그인 시 (provider, provider_user_id)에 해당하는 회원이 없을 때 → 회원가입 유도. */
class MemberNotFoundException(message: String) : RuntimeException(message)

/** ACTIVE가 아닌 회원(정지/탈퇴 등)이 로그인/재발급을 시도할 때. */
class InactiveMemberException(message: String) : RuntimeException(message)

/** 토큰 서명/만료/타입이 유효하지 않을 때. */
class InvalidTokenException(message: String) : RuntimeException(message)

/** 이미 회전·폐기된 refresh가 다시 제출됐을 때(탈취 의심) → 회원 전체 폐기. */
class TokenReuseDetectedException(message: String) : RuntimeException(message)
