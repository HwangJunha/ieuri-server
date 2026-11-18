package org.around.ieuri.auth.application.token.provided

import org.around.ieuri.auth.application.token.provided.dto.LoginCommand
import org.around.ieuri.auth.application.token.provided.dto.LoginEmailCommand
import org.around.ieuri.auth.application.token.provided.dto.LogoutCommand
import org.around.ieuri.auth.application.token.provided.dto.LogoutResult
import org.around.ieuri.auth.application.token.provided.dto.RefreshTokenCommand
import org.around.ieuri.auth.application.token.provided.dto.TokenPairResult

/** 로그인·refresh 회전·로그아웃. [ipAddress]는 접속기록용이며 리졸버가 요청에서 읽어 넘긴다(알 수 없으면 null). */
interface AuthTokenUseCase {
    /** SNS 로그인 → 토큰 발급. 기존 회원만 인증한다(미가입은 거부). */
    fun loginBySns(command: LoginCommand, ipAddress: String?): TokenPairResult

    /** 자체(EMAIL) 로그인 → 토큰 발급. 이메일 미인증은 여기서 막지 않는다. */
    fun loginByEmail(command: LoginEmailCommand, ipAddress: String?): TokenPairResult

    /** refresh 토큰으로 accessToken 재발급(회전). */
    fun refresh(command: RefreshTokenCommand): TokenPairResult

    /** 로그아웃(refresh 폐기). */
    fun logout(command: LogoutCommand, ipAddress: String?): LogoutResult
}
