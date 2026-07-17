package org.around.ieuri.auth.application.port.`in`.command

import org.around.ieuri.auth.application.command.LoginCommand
import org.around.ieuri.auth.application.command.LogoutCommand
import org.around.ieuri.auth.application.command.RefreshTokenCommand

/** 로그인 → 토큰 발급 유스케이스. */
interface LoginUseCase {
    fun handle(command: LoginCommand): TokenPairResult
}

/** refresh 토큰으로 accessToken 재발급(회전) 유스케이스. */
interface RefreshTokenUseCase {
    fun handle(command: RefreshTokenCommand): TokenPairResult
}

/** 로그아웃(refresh 폐기) 유스케이스. */
interface LogoutUseCase {
    fun handle(command: LogoutCommand): LogoutResult
}

/** 발급된 토큰 쌍. expiresIn은 남은 유효기간(초). member_id는 노출하지 않는다. */
data class TokenPairResult(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val accessTokenExpiresInSeconds: Long,
    val refreshTokenExpiresInSeconds: Long,
)

data class LogoutResult(
    val success: Boolean,
)
