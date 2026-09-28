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
    // DGS 기본 핸들러가 아니라 정제 핸들러를 끝에 둔다 — 기본 핸들러는 예외 FQCN과 메시지를
    // 그대로 실어 내부 정보가 클라이언트까지 간다(POL-013).
    private val delegate: DataFetcherExceptionHandler = InternalErrorSanitizingHandler(),
) : DataFetcherExceptionHandler {

    override fun handleException(
        parameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val unwrapped = unwrap(parameters.exception)
        val code = when (unwrapped) {
            is UnauthorizedException -> "UNAUTHENTICATED"

            is ForbiddenException -> "FORBIDDEN"

            // 토큰류 실패도 인증 실패다(SPEC-0024). 여기 없으면 DGS 기본 핸들러가 코드 없이
            // `errorType: INTERNAL`로 내보내고 예외 FQCN까지 메시지에 실린다 —
            // 클라이언트의 자동 재로그인은 이 코드로 분기하므로 그때 동작하지 못한다.
            is TokenAuthenticationException -> unwrapped.code

            // 두 도메인이 함께 쓰는 조회 인자 오류. 여기서 코드를 실어야 클라이언트가 분기한다.
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
     * 감싸인 예외를 푼다. **이것이 없으면 인증 실패가 INTERNAL_ERROR로 나간다.**
     *
     * DGS는 리졸버 메서드를 리플렉션으로 호출하므로, 리졸버가 던진 예외가
     * `InvocationTargetException`(또는 그것을 다시 감싼 `CompletionException`)에 담겨
     * 도착한다. `parameters.exception`을 그대로 `is`로 보면 그 껍데기에 걸려 아래
     * 정제 핸들러까지 흘러가고, 클라이언트는 코드 없는 `INTERNAL_ERROR`를 받는다 —
     * 자동 토큰 갱신은 `UNAUTHENTICATED`를 보고 도는 링크 계층에서 일어나므로 그때
     * 갱신이 트리거되지 않아 재로그인 화면으로도 못 간다(실측: conversations 조회).
     *
     * 원인 사슬을 끝까지 따라가되 **자기 자신을 cause로 갖는 예외에서 멈춘다** —
     * 그런 경우 무한 루프가 된다.
     */
    private fun unwrap(exception: Throwable): Throwable {
        var current = exception
        while (true) {
            val cause = current.cause ?: return current
            if (cause === current) return current
            // 이미 우리가 아는 예외면 더 풀지 않는다 — 그 예외의 cause까지 내려가면
            // 엉뚱한 하위 원인으로 분류될 수 있다.
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
 * 코드를 extensions에 싣는 최상위 오류(POL-016).
 *
 * `internal`인 이유는 미인증 게이트가 이 형태를 재사용하기 때문이다 — 게이트는
 * DataFetcher 바깥(Instrumentation)에서 거절하므로 이 핸들러를 거치지 못하고
 * 오류 객체를 직접 만들어 던진다. 두 곳이 extensions 모양을 따로 갖지 않게 한다.
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
