package org.around.ieuri.auth.domain.vo

/**
 * 이메일 검증 챌린지의 용도(SPEC-0011 R7).
 *
 * 코드 발급·만료·시도 상한 규칙은 둘이 같지만 **코드를 보내는 주소가 다르다** —
 * 가입은 회원의 현재 이메일로, 변경은 아직 회원의 것이 아닌 새 주소로 보낸다.
 * 재발송 쿨다운도 용도별로 따로 센다(한쪽을 보낸 직후 다른 쪽이 막히면 안 된다).
 */
enum class VerificationPurpose {
    /** 가입 시 이메일 소유 확인. 통과하면 member.email_verified가 켜진다. */
    REGISTRATION,

    /** 이메일 변경. 통과하면 회원의 이메일이 대상 주소로 교체된다. */
    EMAIL_CHANGE,
}
