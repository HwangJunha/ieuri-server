package org.around.ieuri.auth.application.exception

import org.around.ieuri.common.security.TokenAuthenticationException

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

/**
 * 토큰 서명/만료/타입이 유효하지 않을 때. 최상위 오류 `INVALID_TOKEN`으로 나간다.
 *
 * 클라이언트는 이 코드를 받으면 refresh로 재발급을 시도한다 — 세션이 끊긴 것은 아니다.
 */
class InvalidTokenException(message: String) :
    TokenAuthenticationException("INVALID_TOKEN", message)

/**
 * 이미 회전·폐기된 refresh가 다시 제출됐을 때(탈취 의심) → 회원 전체 폐기.
 * 최상위 오류 `TOKEN_REUSE_DETECTED`로 나간다.
 *
 * **[InvalidTokenException]과 코드를 나누는 이유**: 이쪽은 재발급으로 회복되지 않는다.
 * 회원의 모든 refresh가 폐기됐으므로 클라이언트는 재로그인 화면으로 보내야 한다.
 */
class TokenReuseDetectedException(message: String) :
    TokenAuthenticationException("TOKEN_REUSE_DETECTED", message)

/**
 * 토큰 저장소(Valkey)에 닿지 못해 판정을 내릴 수 없을 때. 최상위 오류
 * `AUTH_STORE_UNAVAILABLE`로 나간다(SPEC-0032 R6).
 *
 * **요청은 여전히 실패한다** — 저장소를 못 읽었다고 통과시키면 재사용 감지가 무력화되므로
 * fail-closed를 유지한다. 코드만 정확해진다.
 *
 * `INVALID_TOKEN`·`TOKEN_REUSE_DETECTED`와 나누는 이유는 **클라이언트의 행동이 다르기
 * 때문이다.** 저 둘은 토큰 문제라 재발급이나 재로그인으로 넘어가지만, 이쪽은 토큰이
 * 멀쩡한데 서버가 답을 못 하는 상태다 — 재로그인시키면 멀쩡한 세션을 버리게 된다.
 * 클라이언트는 잠시 후 같은 요청을 다시 보내야 한다.
 */
class AuthStoreUnavailableException(message: String, cause: Throwable? = null) :
    TokenAuthenticationException("AUTH_STORE_UNAVAILABLE", message) {
    init {
        cause?.let { initCause(it) }
    }
}

/** 닉네임 길이·형식이 규칙을 벗어날 때(R5). */
class InvalidNicknameException(message: String) :
    UserFacingException("INVALID_NICKNAME", message)

/** 허용하지 않는 이미지 형식으로 업로드 URL을 요청할 때(R6). */
class UnsupportedImageTypeException(message: String) :
    UserFacingException("UNSUPPORTED_IMAGE_TYPE", message)

/** 선언한 크기가 상한을 넘을 때(R6). */
class ImageTooLargeException(message: String) :
    UserFacingException("IMAGE_TOO_LARGE", message)

/** 이 회원에게 발급된 적 없는(또는 형식이 어긋난) 오브젝트 키일 때(R6). */
class ObjectKeyNotFoundException(message: String) :
    UserFacingException("OBJECT_KEY_NOT_FOUND", message)

/** 발급받았으나 실제 업로드가 확인되지 않은 키일 때(R6). */
class PhotoNotUploadedException(message: String) :
    UserFacingException("PHOTO_NOT_UPLOADED", message)

/** 시행 중인 약관에서 코드를 찾을 수 없을 때(R10·R12). */
class TermsNotFoundException(message: String) :
    UserFacingException("TERMS_NOT_FOUND", message)

/** 선택 약관을 필수 동의 경로로 제출할 때(R12). */
class TermsCodeNotRequiredException(message: String) :
    UserFacingException("TERMS_CODE_NOT_REQUIRED", message)

/** 다른 회원이 이미 쓰는 주소로 이메일을 바꾸려 할 때(SPEC-0011 R7). 상대 정보는 담지 않는다(POL-013). */
class EmailAlreadyInUseException(message: String) :
    UserFacingException("EMAIL_ALREADY_IN_USE", message)

/** 현재 자신의 이메일과 같은 주소를 제출할 때(R7). 보낼 코드가 없으므로 요청 자체를 거절한다. */
class SameAsCurrentEmailException(message: String) :
    UserFacingException("SAME_AS_CURRENT_EMAIL", message)

/** 이메일 형식이 올바르지 않을 때(R7). */
class InvalidEmailFormatException(message: String) :
    UserFacingException("INVALID_EMAIL_FORMAT", message)

/**
 * SNS 회원이 이메일 변경을 요청할 때(SPEC-0024 R6). SPEC-0011 R8을 파기한 결과다.
 *
 * SNS 이메일은 provider에 종속된 값이라 우리 쪽에서 바꾸면 provider가 주는 값과 영구히
 * 어긋나고, 되돌릴 방법이 없다.
 */
class NotEmailMemberException(message: String) :
    UserFacingException("NOT_EMAIL_MEMBER", message)

/**
 * 인증을 이미 마친 회원이 인증 주소 재입력을 요청할 때(SPEC-0024 R7-2).
 *
 * 도메인 불변식과 같은 판정이지만 `require`가 내는 `INVALID_INPUT`과 코드를 나눈다 —
 * 클라이언트는 이 코드를 받으면 입력을 고치는 것이 아니라 인증 화면을 벗어나야 한다.
 */
class AlreadyVerifiedException(message: String) :
    UserFacingException("ALREADY_VERIFIED", message)

/**
 * 비밀번호가 정책(8~100자·영문·숫자·특수문자)을 만족하지 않을 때.
 *
 * `INVALID_INPUT`과 코드를 나누는 이유는 클라이언트가 **어느 입력이 문제인지 알아야**
 * 하기 때문이다. 서버 message는 한국어 고정이라 클라이언트가 그대로 노출할 수 없고
 * (POL-022의 4개 언어), 코드가 있어야 자국어 안내를 띄울 수 있다.
 * 어느 규칙을 어겼는지까지는 나누지 않는다 — 클라이언트가 전체 규칙을 안내하면 된다.
 */
class PasswordPolicyViolationException(message: String) :
    UserFacingException("PASSWORD_POLICY_VIOLATION", message)

/** 재발송 쿨다운(60초) 이내 재요청(R7). 기존 VERIFICATION_RATE_LIMITED와 코드를 나눈다 —
 *  클라이언트가 이메일 변경 화면에서만 다른 안내를 띄운다. */
class ResendCooldownActiveException(message: String) :
    UserFacingException("RESEND_COOLDOWN_ACTIVE", message)
