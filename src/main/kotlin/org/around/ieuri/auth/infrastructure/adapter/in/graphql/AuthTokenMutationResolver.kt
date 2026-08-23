package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.application.command.LoginCommand
import org.around.ieuri.auth.application.command.LoginEmailCommand
import org.around.ieuri.auth.application.command.LogoutCommand
import org.around.ieuri.auth.application.command.RefreshTokenCommand
import org.around.ieuri.auth.application.port.`in`.command.AuthTokenUseCase
import org.around.ieuri.auth.application.port.`in`.command.TokenPairResult
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.common.graphql.generated.types.AuthTokenPayload
import org.around.ieuri.common.graphql.generated.types.LoginEmailInput
import org.around.ieuri.common.graphql.generated.types.LoginSnsInput
import org.around.ieuri.common.graphql.generated.types.LogoutInput
import org.around.ieuri.common.graphql.generated.types.LogoutPayload
import org.around.ieuri.common.graphql.generated.types.RefreshTokenInput
import org.around.ieuri.common.graphql.generated.types.UserError

/**
 * 로그인/토큰 GraphQL 뮤테이션 리졸버(인바운드 어댑터). 유스케이스에 위임하고 응답 페이로드로 매핑한다.
 * 입력/출력 타입은 auth.graphqls로부터 codegen으로 생성된다.
 */
@DgsComponent
class AuthTokenMutationResolver(
    private val authTokenUseCase: AuthTokenUseCase,
) {
    @DgsMutation
    fun loginSns(@InputArgument input: LoginSnsInput): AuthTokenPayload = withUserErrors(
        onError = { authTokenErrorPayload(it) },
    ) {
        authTokenUseCase.loginBySns(
            LoginCommand(
                provider = Provider.valueOf(input.provider.name),
                idToken = input.idToken,
                authorizationCode = input.authorizationCode,
            ),
        ).toPayload()
    }

    @DgsMutation
    fun loginEmail(@InputArgument input: LoginEmailInput): AuthTokenPayload = withUserErrors(
        onError = { authTokenErrorPayload(it) },
    ) {
        authTokenUseCase.loginByEmail(LoginEmailCommand(email = input.email, password = input.password)).toPayload()
    }

    @DgsMutation
    fun refreshToken(@InputArgument input: RefreshTokenInput): AuthTokenPayload = withUserErrors(
        onError = { authTokenErrorPayload(it) },
    ) {
        authTokenUseCase.refresh(RefreshTokenCommand(input.refreshToken)).toPayload()
    }

    @DgsMutation
    fun logout(@InputArgument input: LogoutInput): LogoutPayload = withUserErrors(
        onError = { LogoutPayload(success = false, userErrors = it) },
    ) {
        val result = authTokenUseCase.logout(LogoutCommand(input.refreshToken))
        LogoutPayload(success = result.success, userErrors = emptyList())
    }

    private fun TokenPairResult.toPayload(): AuthTokenPayload = AuthTokenPayload(
        success = true,
        userErrors = emptyList(),
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenType = tokenType,
        accessTokenExpiresIn = accessTokenExpiresInSeconds.toInt(),
        refreshTokenExpiresIn = refreshTokenExpiresInSeconds.toInt(),
    )

    private fun authTokenErrorPayload(userErrors: List<UserError>): AuthTokenPayload = AuthTokenPayload(
        success = false,
        userErrors = userErrors,
        accessToken = null,
        refreshToken = null,
        tokenType = null,
        accessTokenExpiresIn = null,
        refreshTokenExpiresIn = null,
    )
}
