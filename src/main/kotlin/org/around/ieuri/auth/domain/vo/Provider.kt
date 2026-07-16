package org.around.ieuri.auth.domain.vo

/**
 * 지원하는 SNS 인증 제공자. 자체 회원가입은 없다(SNS 전용).
 * 다계정 허용: 서로 다른 provider는 별개 회원으로 취급한다.
 */
enum class Provider {
    APPLE,
    GOOGLE,
    KAKAO,
}
