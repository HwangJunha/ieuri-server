package org.around.ieuri.auth.adapter.graphql.resolver
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.auth.adapter.graphql.mapper.toPayload
import org.around.ieuri.auth.adapter.graphql.support.withUserErrors
import org.around.ieuri.auth.application.token.provided.AuthTokenUseCase
import org.around.ieuri.auth.application.token.provided.dto.LoginCommand
import org.around.ieuri.auth.application.token.provided.dto.LoginEmailCommand
import org.around.ieuri.auth.application.token.provided.dto.LogoutCommand
import org.around.ieuri.auth.application.token.provided.dto.RefreshTokenCommand
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.common.graphql.clientIp
import org.around.ieuri.common.graphql.generated.types.AuthTokenPayload
import org.around.ieuri.common.graphql.generated.types.LoginEmailInput
import org.around.ieuri.common.graphql.generated.types.LoginSnsInput
import org.around.ieuri.common.graphql.generated.types.LogoutInput
import org.around.ieuri.common.graphql.generated.types.LogoutPayload
import org.around.ieuri.common.graphql.generated.types.RefreshTokenInput
import org.around.ieuri.common.graphql.generated.types.UserError

/** 로그인/토큰 뮤테이션 리졸버. 유스케이스에 위임하고 응답 페이로드로 매핑한다. */
@DgsComponent
class AuthTokenMutationResolver(
    private val authTokenUseCase: AuthTokenUseCase,
) {
    @DgsMutation
    fun loginSns(
        @InputArgument input: LoginSnsInput,
        dfe: DataFetchingEnvironment,
    ): AuthTokenPayload = withUserErrors(
        onError = { authTokenErrorPayload(it) },
    ) {
        authTokenUseCase.loginBySns(
            LoginCommand(
                provider = Provider.valueOf(input.provider.name),
                idToken = input.idToken,
                authorizationCode = input.authorizationCode,
                ipAddress = dfe.clientIp(),
            ),
        ).toPayload()
    }

    @DgsMutation
    fun loginEmail(
        @InputArgument input: LoginEmailInput,
        dfe: DataFetchingEnvironment,
    ): AuthTokenPayload = withUserErrors(
        onError = { authTokenErrorPayload(it) },
    ) {
        authTokenUseCase.loginByEmail(
            LoginEmailCommand(
                email = input.email,
                password = input.password,
                ipAddress = dfe.clientIp(),
            ),
        ).toPayload()
    }

    @DgsMutation
    fun refreshToken(@InputArgument input: RefreshTokenInput): AuthTokenPayload = withUserErrors(
        onError = { authTokenErrorPayload(it) },
    ) {
        authTokenUseCase.refresh(RefreshTokenCommand(input.refreshToken)).toPayload()
    }

    @DgsMutation
    fun logout(
        @InputArgument input: LogoutInput,
        dfe: DataFetchingEnvironment,
    ): LogoutPayload = withUserErrors(
        onError = { LogoutPayload(success = false, userErrors = it) },
    ) {
        val result = authTokenUseCase.logout(
            LogoutCommand(refreshToken = input.refreshToken, ipAddress = dfe.clientIp()),
        )
        LogoutPayload(success = result.success, userErrors = emptyList())
    }

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
