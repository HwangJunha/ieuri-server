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
 *
 * 단, **인증 실패는 이 분기와 무관하게 항상 최상위 errors[]로 표면화한다**(SPEC-0005 R15).
 * userErrors[]는 세션 상태를 나타내는 값을 담지 않는다 — 클라이언트의 자동 토큰 갱신은 최상위 errors[]를
 * 보는 링크 계층에서 일어나므로, 인증 실패를 payload에 담으면 갱신이 트리거되지 않는다.
 * 따라서 리졸버는 common/security의 UnauthorizedException(= 이 taxonomy 밖)을 그대로 전파시킨다.
 */

/** reactToSpot의 사용자 노출 실패. code는 머신 판독용 안정 식별자. 인증 실패는 여기에 속하지 않는다(위 참조). */
abstract class ReactionUserException(val code: String, message: String) : RuntimeException(message)

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
    enum class Classification { BAD_REQUEST, FAILED_PRECONDITION, UNAVAILABLE, NOT_FOUND, RESOURCE_EXHAUSTED }
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

/**
 * spotDetail 쿼리에서 contentId가 현재 언어 서비스에서 콘텐츠로 해소되지 않음(없거나 무효, 언어 교차 id 포함, AC-15/AC-6).
 * 쿼리 경로이므로 최상위 errors[]로 표면화된다(reactToSpot의 SpotNotFoundException(userError)과 별개).
 */
class SpotDetailNotFoundException(message: String = "해당 관광지를 찾을 수 없습니다.") :
    SpotQueryException("SPOT_NOT_FOUND", Classification.NOT_FOUND, message)

/** TourAPI 일일 호출 한도 초과(캐시 미스 시, AC-16). UPSTREAM_UNAVAILABLE과 구분해 클라가 다르게 안내한다. */
class UpstreamRateLimitedException(message: String = "잠시 후 다시 시도해 주세요.", cause: Throwable? = null) :
    SpotQueryException("UPSTREAM_RATE_LIMITED", Classification.RESOURCE_EXHAUSTED, message, cause)

/**
 * 지도 뷰포트·혼잡도 조회의 실패(SPEC-0014 §10). 기존 쿼리 오류와 같은 경로로 최상위
 * errors[]에 실린다.
 *
 * **서울 API 장애는 여기에 속하지 않는다** — degraded 표식이 붙은 정상 응답으로 표현한다
 * (R11). 예보 부재·상권 부재도 마찬가지로 오류가 아니다.
 */

/** bbox가 뒤집혔거나 좌표가 유효 범위 밖(R4). */
class InvalidBboxException(message: String = "지도 영역이 올바르지 않습니다.") :
    SpotQueryException("INVALID_BBOX", Classification.BAD_REQUEST, message)

/** 뷰포트 면적이 상한 초과(R5). 전 지구 bbox로 전체를 긁어가는 것을 막는다. */
class BboxTooLargeException(message: String = "지도를 확대해 주세요.") :
    SpotQueryException("BBOX_TOO_LARGE", Classification.BAD_REQUEST, message)

/** 줌이 카카오맵 유효 범위 밖(R4). */
class InvalidZoomException(message: String = "지도 확대 수준이 올바르지 않습니다.") :
    SpotQueryException("INVALID_ZOOM", Classification.BAD_REQUEST, message)

/**
 * 혼잡도 조회에 areaIds도 bbox도 없음(R6). 범위 없는 전체 조회는 일일 쿼터를 한 번에
 * 소진시키므로 거절한다.
 */
class AreaFilterRequiredException(message: String = "조회할 구역 또는 지도 영역이 필요합니다.") :
    SpotQueryException("AREA_FILTER_REQUIRED", Classification.BAD_REQUEST, message)

/** 121곳에 없는 구역 id(R6). */
class CongestionAreaNotFoundException(message: String = "해당 구역을 찾을 수 없습니다.") :
    SpotQueryException("CONGESTION_AREA_NOT_FOUND", Classification.NOT_FOUND, message)

/** 구역 id 개수가 상한 초과(R6). 쿼터 보호가 목적이라 클램프가 아니라 거절이다. */
class TooManyAreaIdsException(message: String = "한 번에 조회할 수 있는 구역 수를 초과했습니다.") :
    SpotQueryException("TOO_MANY_AREA_IDS", Classification.BAD_REQUEST, message)
