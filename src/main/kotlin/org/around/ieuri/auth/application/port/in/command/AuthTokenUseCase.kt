package org.around.ieuri.auth.application.port.`in`.command

import org.around.ieuri.auth.application.command.LoginCommand
import org.around.ieuri.auth.application.command.LoginEmailCommand
import org.around.ieuri.auth.application.command.LogoutCommand
import org.around.ieuri.auth.application.command.RefreshTokenCommand

/**
 * 인증 토큰 계열 유스케이스(쓰기). SNS 로그인·refresh 회전·로그아웃을 하나의 응집된 포트로 묶는다.
 */
interface AuthTokenUseCase {
    /** SNS 로그인 → 토큰 발급. 기존 회원만 인증한다(미가입은 거부). */
    fun loginBySns(command: LoginCommand): TokenPairResult

    /** 자체(EMAIL) 로그인 → 토큰 발급. 비밀번호 검증 + 이메일 인증 완료를 요구한다. */
    fun loginByEmail(command: LoginEmailCommand): TokenPairResult

    /** refresh 토큰으로 accessToken 재발급(회전). */
    fun refresh(command: RefreshTokenCommand): TokenPairResult

    /** 로그아웃(refresh 폐기). */
    fun logout(command: LogoutCommand): LogoutResult
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
