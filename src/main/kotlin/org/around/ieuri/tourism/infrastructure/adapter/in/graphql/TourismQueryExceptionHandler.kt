package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.exceptions.DefaultDataFetcherExceptionHandler
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.ResultPath
import graphql.language.SourceLocation
import org.around.ieuri.tourism.application.exception.SpotQueryException
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * 쿼리(spots/availableSpotCategories) 도메인 오류를 최상위 errors[]로 내리되, DGS ErrorType으로 분류한다(spec §4.8).
 * 이 스키마엔 쿼리용 payload 컨벤션이 없어(뮤테이션만 BaseResponse) 쿼리 오류는 errors[]로 표면화한다.
 *
 * SpotQueryException이 아니면 DGS 기본 핸들러(DefaultDataFetcherExceptionHandler)에 위임한다 — auth 등 다른
 * 도메인·시스템 오류는 기존 DGS 처리를 그대로 유지한다(이 빈이 @ConditionalOnMissingBean 기본 빈을 대체하므로).
 * extensions.classification에 BAD_REQUEST/FAILED_PRECONDITION/UNAVAILABLE을, code에 논리 코드 문자열을 담는다.
 */
@Component
class TourismQueryExceptionHandler(
    private val delegate: DefaultDataFetcherExceptionHandler = DefaultDataFetcherExceptionHandler(),
) : DataFetcherExceptionHandler {

    override fun handleException(
        parameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
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

/**
 * SpotQueryException을 표현하는 GraphQLError. classification(ErrorType 이름)과 code를 extensions에 담는다.
 * DGS/graphql-java의 ErrorClassification 인터페이스를 직접 구현해 분류 문자열을 노출한다.
 */
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

    override fun getErrorType(): graphql.ErrorClassification =
        graphql.ErrorClassification.errorClassification(classification.name)

    override fun getExtensions(): Map<String, Any> = mapOf(
        "classification" to classification.name,
        "code" to logicalCode,
    )
}
