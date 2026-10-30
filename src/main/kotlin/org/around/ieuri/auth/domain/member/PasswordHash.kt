package org.around.ieuri.auth.domain.member
/** 비밀번호 단방향 해시 값. 평문 비밀번호는 도메인에 절대 들어오지 않는다. */
@JvmInline
value class PasswordHash(val value: String) {
    init {
        require(value.isNotBlank()) { "비밀번호 해시는 비어 있을 수 없습니다." }
    }
}
