package org.around.ieuri.auth.application.token.provided.dto

/** accessToken 재발급 커맨드(refresh 회전). */
data class RefreshTokenCommand(
    val refreshToken: String,
) {
    init {
        require(refreshToken.isNotBlank()) { "refreshToken은 필수입니다." }
    }
}
