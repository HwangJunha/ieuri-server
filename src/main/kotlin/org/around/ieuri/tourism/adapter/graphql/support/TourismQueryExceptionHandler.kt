package org.around.ieuri.tourism.adapter.graphql.support

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.ResultPath
import graphql.language.SourceLocation
import org.around.ieuri.common.security.SecurityGraphqlExceptionHandler
import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * tourism 쿼리 도메인 오류(SpotQueryException)를 최상위 errors[]로 내리며 DGS ErrorType으로 분류한다(spec §4.8).
 * - 앱의 유일한 핸들러 빈이라 체인의 입구다: tourism → SecurityGraphqlExceptionHandler → InternalErrorSanitizingHandler.
 * - extensions.classification에 ErrorType 이름을, code에 논리 코드 문자열을 담는다.
 */
@Component
class TourismQueryExceptionHandler(
    // 인증·권한 실패를 코드와 함께 내리는 공용 핸들러 — 없으면 DGS 기본 핸들러가 "Unknown GraphQL error"로 삼킨다.
    private val delegate: DataFetcherExceptionHandler = SecurityGraphqlExceptionHandler(),
) : DataFetcherExceptionHandler {

    override fun handleException(
        parameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        // 겉 예외만 본다. 원인 사슬을 풀면 응답 코드가 dev와 달라진다(SPEC-0039 D-112).
        val ex = parameters.exception
        if (ex !is SpotQueryException) {
            return delegate.handleException(parameters)
        }

        val error = SpotQueryGraphQLError(
            message = ex.message ?: ex.logicalCode,
            path = parameters.path,
            location = parameters.sourceLocation,
            logicalCode = ex.logicalCode,
            classification = ex.classification,
        )
        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(error).build(),
        )
    }
}

/** SpotQueryException을 표현하는 GraphQLError. classification과 code를 extensions에 담는다. */
private class SpotQueryGraphQLError(
    private val message: String,
    private val path: ResultPath?,
    private val location: SourceLocation?,
    private val logicalCode: String,
    private val classification: SpotQueryException.Classification,
) : GraphQLError {

    override fun getMessage(): String = message

    override fun getLocations(): List<SourceLocation> = location?.let { listOf(it) } ?: emptyList()

    override fun getPath(): List<Any>? = path?.toList()

    override fun getErrorType(): graphql.ErrorClassification = graphql.ErrorClassification.errorClassification(classification.name)

    override fun getExtensions(): Map<String, Any> = mapOf(
        "classification" to classification.name,
        "code" to logicalCode,
    )
}
