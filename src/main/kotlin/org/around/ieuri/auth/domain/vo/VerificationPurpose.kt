package org.around.ieuri.auth.domain.vo

/** 이메일 검증 챌린지의 용도(SPEC-0011 R7). 코드를 보내는 주소가 다르고, 재발송 쿨다운도 용도별로 센다. */
enum class VerificationPurpose {
    /** 가입 시 이메일 소유 확인. 통과하면 member.email_verified가 켜진다. */
    REGISTRATION,

    /** 이메일 변경. 통과하면 회원의 이메일이 대상 주소로 교체된다. */
    EMAIL_CHANGE,
}
