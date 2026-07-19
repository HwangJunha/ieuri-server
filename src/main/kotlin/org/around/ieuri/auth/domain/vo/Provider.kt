package org.around.ieuri.auth.domain.vo

/**
 * 인증 제공자. SNS(Apple/Google/Kakao)와 자체 이메일 가입(EMAIL)을 구분한다.
 * 다계정 허용: 서로 다른 provider는 별개 회원으로 취급한다.
 * EMAIL은 provider_user_id를 정규화된 이메일로 쓰고 비밀번호 해시를 보관한다.
 */
enum class Provider {
    APPLE,
    GOOGLE,
    KAKAO,
    EMAIL,
}
