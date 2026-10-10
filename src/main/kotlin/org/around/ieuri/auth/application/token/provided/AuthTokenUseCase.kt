package org.around.ieuri.auth.application.token.provided
import org.around.ieuri.auth.application.token.provided.dto.LoginCommand
import org.around.ieuri.auth.application.token.provided.dto.LoginEmailCommand
import org.around.ieuri.auth.application.token.provided.dto.LogoutCommand
import org.around.ieuri.auth.application.token.provided.dto.LogoutResult
import org.around.ieuri.auth.application.token.provided.dto.RefreshTokenCommand
import org.around.ieuri.auth.application.token.provided.dto.TokenPairResult
/** 인증 토큰 유스케이스: 로그인·refresh 회전·로그아웃. */
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
