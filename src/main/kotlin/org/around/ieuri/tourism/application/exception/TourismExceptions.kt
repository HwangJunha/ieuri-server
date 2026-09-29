package org.around.ieuri.tourism.application.exception

/*
 * tourism 도메인 예외 taxonomy.
 * - [ReactionUserException]: reactToSpot 뮤테이션의 예상 실패 → payload의 userErrors.
 * - [SpotQueryException]: 쿼리의 도메인 실패 → 최상위 errors[], [SpotQueryException.classification]으로 분류.
 * - 인증 실패는 이 taxonomy 밖이다 — UnauthorizedException을 그대로 전파해 항상 최상위 errors[]로 낸다(SPEC-0005 R15).
 */

/** reactToSpot의 사용자 노출 실패. code는 머신 판독용 안정 식별자. 인증 실패는 여기에 속하지 않는다. */
abstract class ReactionUserException(val code: String, message: String) : RuntimeException(message)

/** 카탈로그 자체가 없다. 어떤 언어로 물어도 없는 id다. */
class SpotNotFoundException(message: String = "해당 관광지를 찾을 수 없습니다.") : ReactionUserException("SPOT_NOT_FOUND", message)

/**
 * 카탈로그는 있으나 활성 언어에 원본이 없다(SPEC-0029 R-12). 뮤테이션 경로의 userError다.
 * - [SpotNotFoundException]과 구분한다 — 언어를 바꾸면 볼 수 있는 장소다(AC-12.3).
 */
class SpotNotFoundInLocaleException(message: String = "이 언어에서는 볼 수 없는 장소입니다.") : ReactionUserException("SPOT_NOT_FOUND_IN_LOCALE", message)

/**
 * 쿼리의 도메인 실패. [logicalCode]는 클라이언트 분기 기준 문자열, [classification]은 DGS ErrorType 이름이다.
 * - 어댑터가 graphql.ErrorType으로 매핑한다.
 */
abstract class SpotQueryException(
    val logicalCode: String,
    val classification: Classification,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** GraphQL ErrorType로 매핑되는 분류. */
    enum class Classification { BAD_REQUEST, FAILED_PRECONDITION, UNAVAILABLE, NOT_FOUND, RESOURCE_EXHAUSTED }
}

/** 위경도 범위 초과/형식 오류. */
class InvalidCoordinatesException(message: String) : SpotQueryException("INVALID_COORDINATES", Classification.BAD_REQUEST, message)

/** 반경 상한(100,000m) 초과. */
class RadiusTooLargeException(message: String) : SpotQueryException("RADIUS_TOO_LARGE", Classification.BAD_REQUEST, message)

/**
 * `category` 테이블에 없는 카테고리 코드로 상세 하위 필드를 물었다(SPEC-0020).
 * - 필터에는 이 검증이 없다 — 없는 코드로 거르면 0건일 뿐이다.
 */
class UnknownCategoryException(message: String = "알 수 없는 카테고리입니다.") : SpotQueryException("UNKNOWN_CATEGORY", Classification.BAD_REQUEST, message)

/** TourAPI 장애·타임아웃·오류(XML 오류 포함). DB 폴백 없음. */
class UpstreamUnavailableException(message: String, cause: Throwable? = null) : SpotQueryException("UPSTREAM_UNAVAILABLE", Classification.UNAVAILABLE, message, cause)

/** 카탈로그 자체가 없다. 쿼리 경로라 최상위 errors[]로 나간다. */
class SpotDetailNotFoundException(message: String = "해당 관광지를 찾을 수 없습니다.") : SpotQueryException("SPOT_NOT_FOUND", Classification.NOT_FOUND, message)

/**
 * 카탈로그는 있으나 활성 언어에 원본이 없다(SPEC-0029 R-12). 쿼리 경로의 실패다.
 * - [SpotDetailNotFoundException]과 구분한다(AC-12.3).
 * - 목록은 활성 언어로 걸러지므로 딥링크·푸시 같은 우회 진입에서만 도달한다.
 */
class SpotNotFoundInLocaleQueryException(message: String = "이 언어에서는 볼 수 없는 장소입니다.") : SpotQueryException("SPOT_NOT_FOUND_IN_LOCALE", Classification.NOT_FOUND, message)

/** TourAPI 일일 호출 한도 초과(AC-16). UPSTREAM_UNAVAILABLE과 구분해 클라가 다르게 안내한다. */
class UpstreamRateLimitedException(message: String = "잠시 후 다시 시도해 주세요.", cause: Throwable? = null) : SpotQueryException("UPSTREAM_RATE_LIMITED", Classification.RESOURCE_EXHAUSTED, message, cause)

/**
 * TourAPI 초당 호출 한도 초과(SPEC-0037).
 * - 일일 한도([UpstreamRateLimitedException])와 달리 잠시 뒤 재시도하면 풀린다 — 배치는 둘을 섞지 않는다.
 * - 클라이언트에게는 같은 오류 코드를 낸다.
 */
class UpstreamTooManyRequestsException(message: String = "잠시 후 다시 시도해 주세요.", cause: Throwable? = null) : SpotQueryException("UPSTREAM_RATE_LIMITED", Classification.RESOURCE_EXHAUSTED, message, cause)

/*
 * 지도 뷰포트·혼잡도 조회의 실패(SPEC-0014).
 * - 서울 API 장애·예보 부재·상권 부재는 오류가 아니라 degraded 표식이 붙은 정상 응답이다.
 */

/** bbox가 뒤집혔거나 좌표가 유효 범위 밖(R4). */
class InvalidBboxException(message: String = "지도 영역이 올바르지 않습니다.") : SpotQueryException("INVALID_BBOX", Classification.BAD_REQUEST, message)

/** 뷰포트 면적이 상한 초과(R5). */
class BboxTooLargeException(message: String = "지도를 확대해 주세요.") : SpotQueryException("BBOX_TOO_LARGE", Classification.BAD_REQUEST, message)

/** 줌이 카카오맵 유효 범위 밖(R4). */
class InvalidZoomException(message: String = "지도 확대 수준이 올바르지 않습니다.") : SpotQueryException("INVALID_ZOOM", Classification.BAD_REQUEST, message)

/** 혼잡도 조회에 areaIds도 bbox도 없음(R6). 범위 없는 전체 조회는 거절한다. */
class AreaFilterRequiredException(message: String = "조회할 구역 또는 지도 영역이 필요합니다.") : SpotQueryException("AREA_FILTER_REQUIRED", Classification.BAD_REQUEST, message)

/** 121곳에 없는 구역 id(R6). */
class CongestionAreaNotFoundException(message: String = "해당 구역을 찾을 수 없습니다.") : SpotQueryException("CONGESTION_AREA_NOT_FOUND", Classification.NOT_FOUND, message)

/** 구역 id 개수가 상한 초과(R6). 클램프가 아니라 거절이다. */
class TooManyAreaIdsException(message: String = "한 번에 조회할 수 있는 구역 수를 초과했습니다.") : SpotQueryException("TOO_MANY_AREA_IDS", Classification.BAD_REQUEST, message)

/**
 * 커서가 유효하지 않다(SPEC-0019). 조작된 값과 참조 축 이관 이전의 구 커서가 모두 여기로 온다.
 * - 클라이언트는 커서를 버리고 첫 페이지부터 다시 요청한다.
 */
class InvalidCursorException(message: String = "유효하지 않은 커서입니다.", cause: Throwable? = null) : SpotQueryException("INVALID_CURSOR", Classification.BAD_REQUEST, message, cause)

/**
 * 검색어가 제출됐으나 트림 후 비었다(SPEC-0033 R6).
 * - 인자 생략·null은 "필터 없음"이라 오류가 아니다.
 * - `LikedSpotConnection`에는 payload 래퍼가 없어 최상위 errors[]로 나간다(`searchSpots`는 userErrors[]).
 */
class EmptyQueryException(message: String = "검색어를 입력해 주세요.") : SpotQueryException("EMPTY_QUERY", Classification.BAD_REQUEST, message)

/**
 * 검색어가 트림 후 길이 상한을 넘었다(SPEC-0033 R6, POL-035).
 * - 메시지에 검색어 원문을 담지 않는다(POL-013).
 */
class QueryTooLongException(maxLength: Int) : SpotQueryException("QUERY_TOO_LONG", Classification.BAD_REQUEST, "검색어는 최대 ${maxLength}자까지 입력할 수 있습니다.")

/**
 * 지역 추천 조회의 개수가 0 이하(SPEC-0015 R29).
 * - 상한 초과는 거절이 아니라 클램프한다(POL-018).
 */
class InvalidLimitException(message: String = "요청 개수가 올바르지 않습니다.") : SpotQueryException("INVALID_LIMIT", Classification.BAD_REQUEST, message)

/** 칸 코드가 존재하지 않는다(SPEC-0030 R7). 시군구 코드를 넣은 경우도 여기로 온다. */
class MapCellNotFoundException(message: String = "해당 지역을 찾을 수 없습니다.") : SpotQueryException("MAP_CELL_NOT_FOUND", Classification.NOT_FOUND, message)

/**
 * 공유 토큰이 없거나 폐기됐다(SPEC-0030 R14).
 * - 둘을 구분해 응답하지 않는다 — 구분하면 공유 이력이 새어 나간다.
 */
class ShareLinkNotFoundException(message: String = "더 이상 볼 수 없는 지도입니다.") : SpotQueryException("SHARE_LINK_NOT_FOUND", Classification.NOT_FOUND, message)

/** 차단 관계라 공유 지도를 볼 수 없다(SPEC-0030 R16, POL-041). */
class ShareLinkBlockedException(message: String = "볼 수 없는 지도입니다.") : SpotQueryException("SHARE_LINK_BLOCKED", Classification.FAILED_PRECONDITION, message)

/** 자기 자신과 관계를 맺으려 했다(SPEC-0030). DB CHECK보다 먼저 서비스가 막는다. */
class CannotRelateSelfException(message: String = "자기 자신과는 관계를 맺을 수 없습니다.") : SpotQueryException("CANNOT_RELATE_SELF", Classification.BAD_REQUEST, message)
