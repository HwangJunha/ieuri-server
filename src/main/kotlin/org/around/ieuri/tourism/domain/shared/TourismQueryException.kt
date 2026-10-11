package org.around.ieuri.tourism.domain.shared

/**
 * 쿼리의 도메인 실패. [logicalCode]는 클라이언트 분기 기준 문자열, [classification]은 DGS ErrorType 이름이다.
 * - 어댑터가 graphql.ErrorType으로 매핑한다.
 */
abstract class TourismQueryException(
    val logicalCode: String,
    val classification: Classification,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** GraphQL ErrorType로 매핑되는 분류. */
    enum class Classification { BAD_REQUEST, FAILED_PRECONDITION, UNAVAILABLE, NOT_FOUND, RESOURCE_EXHAUSTED }
}
