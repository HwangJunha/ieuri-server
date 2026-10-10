package org.around.ieuri.auth.application.member.required
/**
 * 로그인 식별자 보호 포트. EMAIL 회원의 provider_user_id(= 이메일)를 복원 불가능한 형태로 바꾼다.
 * - 같은 입력은 항상 같은 결과여야 한다. 등호 조회와 유니크 제약이 이 값으로 동작한다.
 * - 입력은 정규화된(trim+lowercase) 이메일이어야 한다. SNS 식별자는 변환하지 않는다.
 */
interface LoginIdentifierProtectorPort {
    fun protect(normalizedIdentifier: String): String
}
