package org.around.ieuri.common.security

import org.around.ieuri.common.error.EmptyCategorySetException
import graphql.ErrorClassification
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.ResultPath
import graphql.language.SourceLocation
import com.netflix.graphql.dgs.exceptions.DefaultDataFetcherExceptionHandler
import java.util.concurrent.CompletableFuture

/**
 * 인증·권한 실패를 최상위 `errors[]`에 **코드와 함께** 내린다(POL-016).
 *
 * 이것이 없으면 DGS 기본 핸들러가 두 예외를 그냥 삼켜 `"Unknown GraphQL error"`로 내보낸다 —
 * 메시지도 코드도 없어서 클라이언트는 물론 개발자도 원인을 알 수 없다. 특히 **구독은
 * 실패 지점이 연결 수립·구독 시작 두 곳이라** 구분되지 않으면 디버깅이 불가능하다.
 *
 * 클라이언트의 자동 토큰 갱신은 `UNAUTHENTICATED`를 보고 도는 링크 계층에서 일어나므로,
 * 이 코드가 실려야 갱신이 트리거된다.
 *
 * 다른 예외는 [delegate]에 넘긴다. **빈으로 등록하지 않는다** — 핸들러 빈은 하나뿐이라
 * common이 자리를 차지하면 도메인 핸들러가 밀려난다. 도메인 핸들러가 이 클래스를 감싸
 * 자기 예외를 먼저 보고 나머지를 넘기는 구조다(TourismQueryExceptionHandler 참고).
 */
class SecurityGraphqlExceptionHandler(
    private val delegate: DataFetcherExceptionHandler = DefaultDataFetcherExceptionHandler(),
) : DataFetcherExceptionHandler {

    override fun handleException(
        parameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val code = when (val ex = parameters.exception) {
            is UnauthorizedException -> "UNAUTHENTICATED"
            is ForbiddenException -> "FORBIDDEN"
            // 미인증 게이트(SPEC-0024 R9). 인증류 선행 조건이라 위 둘과 같은 표면으로 낸다.
            is EmailNotVerifiedException -> "EMAIL_NOT_VERIFIED"
            // 두 도메인이 함께 쓰는 조회 인자 오류. 여기서 코드를 실어야 클라이언트가 분기한다.
            is EmptyCategorySetException -> ex.code
            else -> return delegate.handleException(parameters)
        }

        val error = SecurityGraphQLError(
            message = parameters.exception.message ?: code,
            path = parameters.path,
            location = parameters.sourceLocation,
            code = code,
        )
        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(error).build(),
        )
    }
}

private class SecurityGraphQLError(
    private val message: String,
    private val path: ResultPath?,
    private val location: SourceLocation?,
    private val code: String,
) : GraphQLError {

    override fun getMessage(): String = message

    override fun getLocations(): List<SourceLocation> = location?.let { listOf(it) } ?: emptyList()

    override fun getPath(): List<Any>? = path?.toList()

    override fun getErrorType(): ErrorClassification = ErrorClassification.errorClassification(code)

    override fun getExtensions(): Map<String, Any> = mapOf(
        "classification" to code,
        "code" to code,
    )
}
