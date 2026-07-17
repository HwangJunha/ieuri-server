package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.application.command.LoginCommand
import org.around.ieuri.auth.application.command.LogoutCommand
import org.around.ieuri.auth.application.command.RefreshTokenCommand
import org.around.ieuri.auth.application.port.`in`.command.LoginUseCase
import org.around.ieuri.auth.application.port.`in`.command.LogoutUseCase
import org.around.ieuri.auth.application.port.`in`.command.RefreshTokenUseCase
import org.around.ieuri.auth.application.port.`in`.command.TokenPairResult
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.infrastructure.graphql.generated.types.AuthTokenPayload
import org.around.ieuri.auth.infrastructure.graphql.generated.types.LoginInput
import org.around.ieuri.auth.infrastructure.graphql.generated.types.LogoutInput
import org.around.ieuri.auth.infrastructure.graphql.generated.types.LogoutPayload
import org.around.ieuri.auth.infrastructure.graphql.generated.types.RefreshTokenInput

/**
 * 로그인/토큰 GraphQL 뮤테이션 리졸버(인바운드 어댑터). 유스케이스에 위임하고 응답 페이로드로 매핑한다.
 * 입력/출력 타입은 auth.graphqls로부터 codegen으로 생성된다.
 */
@DgsComponent
class AuthTokenMutationResolver(
    private val loginUseCase: LoginUseCase,
    private val refreshTokenUseCase: RefreshTokenUseCase,
    private val logoutUseCase: LogoutUseCase,
) {
    @DgsMutation
    fun login(@InputArgument input: LoginInput): AuthTokenPayload {
        val result = loginUseCase.handle(
            LoginCommand(provider = Provider.valueOf(input.provider.name), idToken = input.idToken),
        )
        return result.toPayload()
    }

    @DgsMutation
    fun refreshToken(@InputArgument input: RefreshTokenInput): AuthTokenPayload {
        val result = refreshTokenUseCase.handle(RefreshTokenCommand(input.refreshToken))
        return result.toPayload()
    }

    @DgsMutation
    fun logout(@InputArgument input: LogoutInput): LogoutPayload {
        val result = logoutUseCase.handle(LogoutCommand(input.refreshToken))
        return LogoutPayload(success = result.success)
    }

    private fun TokenPairResult.toPayload(): AuthTokenPayload = AuthTokenPayload(
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenType = tokenType,
        accessTokenExpiresIn = accessTokenExpiresInSeconds.toInt(),
        refreshTokenExpiresIn = refreshTokenExpiresInSeconds.toInt(),
    )
}
