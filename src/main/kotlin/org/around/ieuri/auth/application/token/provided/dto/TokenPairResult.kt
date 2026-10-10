package org.around.ieuri.auth.application.token.provided.dto
import org.around.ieuri.auth.application.token.provided.dto.LoginCommand
import org.around.ieuri.auth.application.token.provided.dto.LoginEmailCommand
import org.around.ieuri.auth.application.token.provided.dto.LogoutCommand
import org.around.ieuri.auth.application.token.provided.dto.RefreshTokenCommand
/** 발급된 토큰 쌍. expiresIn은 남은 유효기간(초). member_id는 노출하지 않는다. */
data class TokenPairResult(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val accessTokenExpiresInSeconds: Long,
    val refreshTokenExpiresInSeconds: Long,
)
