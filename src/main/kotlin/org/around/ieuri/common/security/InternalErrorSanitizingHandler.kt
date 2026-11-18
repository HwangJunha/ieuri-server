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
 * 매핑되지 않은 예외를 고정 메시지·코드로만 내보낸다(POL-013). 원인은 서버 로그에만 남긴다.
 * 핸들러 체인의 맨 끝에 둔다.
 */
class InternalErrorSanitizingHandler : DataFetcherExceptionHandler {

    override fun handleException(
        parameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val exception = parameters.exception
        // path가 있어야 어느 필드에서 터졌는지 추적할 수 있다.
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
