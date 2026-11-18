package org.around.ieuri.common.security

import graphql.ErrorClassification
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.ResultPath
import graphql.language.SourceLocation
import org.around.ieuri.common.error.EmptyCategorySetException
import java.util.concurrent.CompletableFuture

/**
 * 인증·권한 실패를 최상위 `errors[]`에 코드와 함께 내린다(POL-016). 클라이언트의 토큰 갱신이 이 코드로 돈다.
 * - 다른 예외는 [delegate]에 넘긴다.
 * - 빈으로 등록하지 않는다 — 핸들러 빈은 하나뿐이라 도메인 핸들러가 이 클래스를 감싼다.
 */
class SecurityGraphqlExceptionHandler(
    // DGS 기본 핸들러가 아니라 정제 핸들러를 끝에 둔다 — 기본 핸들러는 예외 FQCN·메시지를 내보낸다(POL-013).
    private val delegate: DataFetcherExceptionHandler = InternalErrorSanitizingHandler(),
) : DataFetcherExceptionHandler {

    override fun handleException(
        parameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val unwrapped = unwrap(parameters.exception)
        val code = when (unwrapped) {
            is UnauthorizedException -> "UNAUTHENTICATED"

            is ForbiddenException -> "FORBIDDEN"

            // 토큰류 실패도 인증 실패다(SPEC-0024).
            is TokenAuthenticationException -> unwrapped.code

            // 두 도메인이 함께 쓰는 조회 인자 오류.
            is EmptyCategorySetException -> unwrapped.code

            else -> return delegate.handleException(parameters)
        }

        val error = SecurityGraphQLError(
            message = unwrapped.message ?: code,
            path = parameters.path,
            location = parameters.sourceLocation,
            code = code,
        )
        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(error).build(),
        )
    }

    /**
     * 감싸인 예외(InvocationTargetException·CompletionException)를 푼다.
     * 없으면 인증 실패가 코드 없는 INTERNAL_ERROR로 나가 토큰 갱신이 트리거되지 않는다.
     * - 자기 자신을 cause로 갖는 예외에서 멈춘다.
     */
    private fun unwrap(exception: Throwable): Throwable {
        var current = exception
        while (true) {
            val cause = current.cause ?: return current
            if (cause === current) return current
            // 이미 아는 예외면 더 풀지 않는다 — 하위 원인으로 잘못 분류될 수 있다.
            if (current.isKnown()) return current
            current = cause
        }
    }

    private fun Throwable.isKnown(): Boolean = this is UnauthorizedException ||
        this is ForbiddenException ||
        this is TokenAuthenticationException ||
        this is EmptyCategorySetException
}

/**
 * 코드를 extensions에 싣는 최상위 오류(POL-016). 미인증 게이트(Instrumentation)도 이 형태를 재사용한다.
 */
internal class SecurityGraphQLError(
    private val message: String,
    private val path: ResultPath? = null,
    private val location: SourceLocation? = null,
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
