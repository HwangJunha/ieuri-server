package org.around.ieuri.auth.application.command

import org.around.ieuri.auth.domain.vo.Provider

/**
 * 로그인 커맨드. SNS 인증 결과(id_token)로 기존 회원을 인증한다.
 * (미가입 회원은 로그인 거부 → 회원가입으로 유도)
 */
data class LoginCommand(
    val provider: Provider,
    val idToken: String,
) {
    init {
        require(idToken.isNotBlank()) { "idToken은 필수입니다." }
    }
}

/** 자체(EMAIL) 로그인 커맨드. 이메일+비밀번호로 인증한다. */
data class LoginEmailCommand(
    val email: String,
    val password: String,
) {
    init {
        require(email.isNotBlank()) { "이메일은 필수입니다." }
        require(password.isNotBlank()) { "비밀번호는 필수입니다." }
    }
}

/** accessToken 재발급 커맨드(refresh 회전). */
data class RefreshTokenCommand(
    val refreshToken: String,
) {
    init {
        require(refreshToken.isNotBlank()) { "refreshToken은 필수입니다." }
    }
}

/** 로그아웃 커맨드. 제출된 refresh를 폐기한다. */
data class LogoutCommand(
    val refreshToken: String,
) {
    init {
        require(refreshToken.isNotBlank()) { "refreshToken은 필수입니다." }
    }
}
