package org.around.ieuri.auth.application.exception

/**
 * 사용자에게 그대로 보여줄 수 있는 예상 가능한 도메인/유스케이스 실패.
 * 인바운드 어댑터(리졸버)는 이 예외를 GraphQL 최상위 error가 아니라 payload의 `userErrors`로 변환한다.
 * [code]는 머신 판독용 안정 식별자(예: MEMBER_ALREADY_REGISTERED)다.
 *
 * 반대로 인증/인가/토큰 실패(InvalidToken/TokenReuse)는 이 타입이 아니며 최상위 error로 전파된다.
 */
abstract class UserFacingException(val code: String, message: String) : RuntimeException(message)

/** 이미 같은 (provider, provider_user_id)로 가입된 회원이 있을 때. */
class MemberAlreadyRegisteredException(message: String) :
    UserFacingException("MEMBER_ALREADY_REGISTERED", message)

/** 검증 핸들(publicId)에 해당하는 챌린지를 찾을 수 없을 때. */
class VerificationNotFoundException(message: String) :
    UserFacingException("VERIFICATION_NOT_FOUND", message)

/** 재발송 쿨다운 이내 재요청 등 rate limit 위반. */
class VerificationRateLimitedException(message: String) :
    UserFacingException("VERIFICATION_RATE_LIMITED", message)

/** 로그인 시 (provider, provider_user_id)에 해당하는 회원이 없을 때 → 회원가입 유도. */
class MemberNotFoundException(message: String) :
    UserFacingException("MEMBER_NOT_FOUND", message)

/** ACTIVE가 아닌 회원(정지/탈퇴 등)이 로그인/재발급을 시도할 때. */
class InactiveMemberException(message: String) :
    UserFacingException("ACCOUNT_INACTIVE", message)

/**
 * SNS id_token 검증 실패(서명 불일치/발급자·audience 불일치/만료/형식 오류).
 * 어떤 사유인지 과도하게 노출하지 않도록 단일 code로 일반화한다.
 */
class SnsVerificationFailedException(message: String) :
    UserFacingException("SNS_VERIFICATION_FAILED", message)

/** 존재하지 않는 역할 코드로 부여/회수를 시도할 때. */
class RoleNotFoundException(message: String) :
    UserFacingException("ROLE_NOT_FOUND", message)

/** 본인의 마지막 관리 역할을 스스로 회수하려 할 때(자기 강등 방지). */
class SelfDemotionException(message: String) :
    UserFacingException("SELF_DEMOTION", message)

/** 자체(EMAIL) 로그인 실패(이메일 없음/비밀번호 불일치/비밀번호 미설정 계정). 계정 존재 여부를 드러내지 않는다. */
class InvalidCredentialsException(message: String) :
    UserFacingException("INVALID_CREDENTIALS", message)

/** 이메일 인증(활성화) 전 계정으로 로그인을 시도할 때. */
class EmailNotVerifiedException(message: String) :
    UserFacingException("EMAIL_NOT_VERIFIED", message)

/** 토큰 서명/만료/타입이 유효하지 않을 때. 인증 실패이므로 최상위 error로 전파된다. */
class InvalidTokenException(message: String) : RuntimeException(message)

/** 이미 회전·폐기된 refresh가 다시 제출됐을 때(탈취 의심) → 회원 전체 폐기. 최상위 error로 전파된다. */
class TokenReuseDetectedException(message: String) : RuntimeException(message)
