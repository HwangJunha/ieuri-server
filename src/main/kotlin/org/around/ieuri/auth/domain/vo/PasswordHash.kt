package org.around.ieuri.auth.domain.vo

/**
 * 비밀번호의 단방향 해시 값. 도메인은 이미 해시된 값만 보관하며 인코딩 방식(BCrypt+pepper 등)은 알지 못한다.
 * 평문 비밀번호는 도메인에 절대 들어오지 않는다(해시는 애플리케이션의 PasswordEncoderPort가 담당).
 */
@JvmInline
value class PasswordHash(val value: String) {
    init {
        require(value.isNotBlank()) { "비밀번호 해시는 비어 있을 수 없습니다." }
    }
}
