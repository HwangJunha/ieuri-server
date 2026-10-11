package org.around.ieuri.auth.application.token.provided.dto

/** 로그아웃 커맨드. 제출된 refresh를 폐기한다. */
data class LogoutCommand(
    val refreshToken: String,
) {
    init {
        require(refreshToken.isNotBlank()) { "refreshToken은 필수입니다." }
    }
}
