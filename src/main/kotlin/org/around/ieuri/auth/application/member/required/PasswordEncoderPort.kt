package org.around.ieuri.auth.application.member.required
/** 비밀번호 단방향 해시 포트(구현은 BCrypt + 서버 pepper). */
interface PasswordEncoderPort {
    fun encode(rawPassword: String): String
    fun matches(rawPassword: String, encodedPassword: String): Boolean
}
