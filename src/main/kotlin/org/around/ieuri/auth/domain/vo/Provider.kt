package org.around.ieuri.auth.domain.vo

/** 인증 제공자. 서로 다른 provider는 별개 회원이다(다계정 허용). EMAIL은 비밀번호 해시를 보관한다. */
enum class Provider {
    APPLE,
    GOOGLE,
    KAKAO,
    EMAIL,
}
