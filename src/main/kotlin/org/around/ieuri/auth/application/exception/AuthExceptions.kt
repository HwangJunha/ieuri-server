package org.around.ieuri.auth.application.exception

/** 이미 같은 (provider, provider_user_id)로 가입된 회원이 있을 때. */
class MemberAlreadyRegisteredException(message: String) : RuntimeException(message)

/** 검증 핸들(publicId)에 해당하는 챌린지를 찾을 수 없을 때. */
class VerificationNotFoundException(message: String) : RuntimeException(message)

/** 재발송 쿨다운 이내 재요청 등 rate limit 위반. */
class VerificationRateLimitedException(message: String) : RuntimeException(message)
