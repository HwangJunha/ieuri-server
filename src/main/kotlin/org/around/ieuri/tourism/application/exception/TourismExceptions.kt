package org.around.ieuri.tourism.application.exception

/**
 * tourism 도메인 예외 taxonomy.
 *
 * 두 갈래로 나뉜다:
 *  - [ReactionUserException]  : reactToSpot 뮤테이션의 예상 실패 → payload의 userErrors로 변환(BaseResponse 규약).
 *  - [SpotQueryException]     : spots/availableSpotCategories 쿼리의 도메인 실패 → 최상위 errors[]로,
 *                               [errorClassification]에 담긴 DGS ErrorType으로 분류(POL-016 초안).
 *
 * 쿼리엔 payload 컨벤션이 없어 두 경로가 다르다(spec §4.8).
 */

/** reactToSpot의 사용자 노출 실패. code는 머신 판독용 안정 식별자. */
abstract class ReactionUserException(val code: String, message: String) : RuntimeException(message)

/** 비로그인 상태의 좋아요/싫어요 시도(AC-14). */
class UnauthenticatedReactionException(message: String = "로그인이 필요합니다.") :
    ReactionUserException("UNAUTHENTICATED", message)

/** 현재 요청 언어에서 resolve되지 않는 spotId(오래된 카드 등, AC-14b). */
class SpotNotFoundException(message: String = "해당 관광지를 찾을 수 없습니다.") :
    ReactionUserException("SPOT_NOT_FOUND", message)

/**
 * spots/availableSpotCategories 쿼리의 도메인 실패. [logicalCode]는 클라이언트 분기 기준 문자열,
 * [classification]은 DGS ErrorType 이름(BAD_REQUEST/FAILED_PRECONDITION/UNAVAILABLE)이다.
 * 인프라(어댑터) 계층은 이 이름을 graphql.ErrorType으로 매핑한다 — application은 GraphQL에 의존하지 않는다.
 */
abstract class SpotQueryException(
    val logicalCode: String,
    val classification: Classification,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** GraphQL ErrorType로 매핑되는 분류(어댑터가 graphql.errors.ErrorType로 변환). */
    enum class Classification { BAD_REQUEST, FAILED_PRECONDITION, UNAVAILABLE }
}

/** 위경도 범위 초과/형식 오류. */
class InvalidCoordinatesException(message: String) :
    SpotQueryException("INVALID_COORDINATES", Classification.BAD_REQUEST, message)

/** 서버 UX/성능 반경 상한(100,000m) 초과. */
class RadiusTooLargeException(message: String) :
    SpotQueryException("RADIUS_TOO_LARGE", Classification.BAD_REQUEST, message)

/** 현재 언어에 없는 카테고리(여행코스/교통 비대칭) 조회 시도. */
class CategoryNotAvailableForLocaleException(message: String) :
    SpotQueryException("CATEGORY_NOT_AVAILABLE_FOR_LOCALE", Classification.FAILED_PRECONDITION, message)

/** TourAPI 장애·타임아웃·오류(XML 오류 포함). DB 폴백 없음. */
class UpstreamUnavailableException(message: String, cause: Throwable? = null) :
    SpotQueryException("UPSTREAM_UNAVAILABLE", Classification.UNAVAILABLE, message, cause)
