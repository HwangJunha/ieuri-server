package org.around.ieuri.common.security

import graphql.ErrorClassification
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.ResultPath
import graphql.language.SourceLocation
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * 매핑되지 않은 예외를 **내부 정보 없이** 내보낸다(POL-013).
 *
 * DGS 기본 핸들러는 `"${예외 FQCN}: ${메시지}"`를 그대로 메시지에 싣는다. 그래서 예컨대
 * JPA 제약 위반이 나면 SQL 문·테이블명·제약명·회원 id가 통째로 클라이언트까지 갔다.
 * 예상하지 못한 실패일수록 그 원문에 무엇이 담길지 알 수 없으므로, **화이트리스트 방식으로
 * 고정 메시지만 내보내고** 원인은 서버 로그에만 남긴다.
 *
 * 스택은 로그로 남기므로 디버깅 정보를 잃지 않는다 — 노출 대상만 바뀐다.
 *
 * 핸들러 체인의 **맨 끝**에 둔다. 앞선 핸들러들이 자기 예외에 코드를 실어 처리하고,
 * 여기 도달했다는 것은 우리가 모르는 실패라는 뜻이다.
 */
class InternalErrorSanitizingHandler : DataFetcherExceptionHandler {

    override fun handleException(
        parameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val exception = parameters.exception
        // 원인은 로그에만 남긴다. path가 있어야 어느 필드에서 터졌는지 추적할 수 있다.
        log.error("처리되지 않은 오류: path={}", parameters.path, exception)

        val error = InternalGraphQLError(
            path = parameters.path,
            location = parameters.sourceLocation,
        )
        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(error).build(),
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(InternalErrorSanitizingHandler::class.java)!!
    }
}

/** 고정 메시지·고정 코드. 예외의 어떤 값도 담지 않는다. */
private class InternalGraphQLError(
    private val path: ResultPath?,
    private val location: SourceLocation?,
) : GraphQLError {

    override fun getMessage(): String = "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."

    override fun getLocations(): List<SourceLocation> = location?.let { listOf(it) } ?: emptyList()

    override fun getPath(): List<Any>? = path?.toList()

    override fun getErrorType(): ErrorClassification = ErrorClassification.errorClassification(CODE)

    override fun getExtensions(): Map<String, Any> = mapOf(
        "classification" to CODE,
        "code" to CODE,
    )

    private companion object {
        const val CODE = "INTERNAL_ERROR"
    }
}
