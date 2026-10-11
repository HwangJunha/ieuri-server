package org.around.ieuri.auth.adapter.graphql.resolver
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.auth.adapter.graphql.mapper.toCommand
import org.around.ieuri.auth.adapter.graphql.mapper.toPayload
import org.around.ieuri.auth.adapter.graphql.support.withAuthUserErrors
import org.around.ieuri.auth.application.token.provided.AuthTokenUseCase
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
    ): AuthTokenPayload = withAuthUserErrors(
        onError = { authTokenErrorPayload(it) },
    ) {
        authTokenUseCase.loginBySns(input.toCommand(), dfe.clientIp()).toPayload()
    }

    @DgsMutation
    fun loginEmail(
        @InputArgument input: LoginEmailInput,
        dfe: DataFetchingEnvironment,
    ): AuthTokenPayload = withAuthUserErrors(
        onError = { authTokenErrorPayload(it) },
    ) {
        authTokenUseCase.loginByEmail(input.toCommand(), dfe.clientIp()).toPayload()
    }

    @DgsMutation
    fun refreshToken(@InputArgument input: RefreshTokenInput): AuthTokenPayload = withAuthUserErrors(
        onError = { authTokenErrorPayload(it) },
    ) {
        authTokenUseCase.refresh(input.toCommand()).toPayload()
    }

    @DgsMutation
    fun logout(
        @InputArgument input: LogoutInput,
        dfe: DataFetchingEnvironment,
    ): LogoutPayload = withAuthUserErrors(
        onError = { LogoutPayload(success = false, userErrors = it) },
    ) {
        val result = authTokenUseCase.logout(input.toCommand(), dfe.clientIp())
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
