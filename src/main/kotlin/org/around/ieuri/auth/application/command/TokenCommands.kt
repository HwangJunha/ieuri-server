package org.around.ieuri.auth.application.command

import org.around.ieuri.auth.domain.vo.Provider

/** SNS 로그인 커맨드. id_token으로 기존 회원을 인증한다(미가입은 거부). */
data class LoginCommand(
    val provider: Provider,
    val idToken: String,
    /** 탈퇴 시 연결 해제용 토큰을 보충하기 위한 provider authorization code. 없으면 보충하지 않는다. */
    val authorizationCode: String? = null,
    /** 접속지 정보(접속기록용). 인바운드 어댑터가 헤더에서 읽어 넘기며, 판정 불가 시 null. */
    val ipAddress: String? = null,
) {
    init {
        require(idToken.isNotBlank()) { "idToken은 필수입니다." }
    }
}

/** 자체(EMAIL) 로그인 커맨드. 이메일+비밀번호로 인증한다. */
data class LoginEmailCommand(
    val email: String,
    val password: String,
    /** 접속지 정보(접속기록용). 규약은 [LoginCommand.ipAddress]와 같다. */
    val ipAddress: String? = null,
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
    /** 접속지 정보(접속기록용). 규약은 [LoginCommand.ipAddress]와 같다. */
    val ipAddress: String? = null,
) {
    init {
        require(refreshToken.isNotBlank()) { "refreshToken은 필수입니다." }
    }
}
