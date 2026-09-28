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

/** 카탈로그 자체가 없다. 어떤 언어로 물어도 없는 id다. */
class SpotNotFoundException(message: String = "해당 관광지를 찾을 수 없습니다.") : ReactionUserException("SPOT_NOT_FOUND", message)

/**
 * 카탈로그는 있으나 **활성 언어에 원본이 없다**(SPEC-0029 R-12). 뮤테이션 경로의 userError다.
 *
 * [SpotNotFoundException]과 반드시 구분한다 — 클라이언트가 다른 안내를 띄운다. 이쪽은 언어를
 * 바꾸면 볼 수 있는 장소이고, 저쪽은 어느 언어에서도 없는 id다(AC-12.3).
 */
class SpotNotFoundInLocaleException(message: String = "이 언어에서는 볼 수 없는 장소입니다.") : ReactionUserException("SPOT_NOT_FOUND_IN_LOCALE", message)

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
class InvalidCoordinatesException(message: String) : SpotQueryException("INVALID_COORDINATES", Classification.BAD_REQUEST, message)

/** 서버 UX/성능 반경 상한(100,000m) 초과. */
class RadiusTooLargeException(message: String) : SpotQueryException("RADIUS_TOO_LARGE", Classification.BAD_REQUEST, message)

/**
 * `category` 테이블에 없는 카테고리 코드로 상세 하위 필드를 물었다(SPEC-0020).
 *
 * **필터에는 이 검증이 없다** — 없는 코드로 거른 목록은 결과가 0건일 뿐이라 해가 없다. 반면
 * `intro`·`infoItems`는 코드가 캐시 키와 적재 행의 일부가 되므로, 검증하지 않으면 임의의
 * 문자열마다 캐시 키와 빈 행이 하나씩 생긴다.
 *
 * 유효 집합은 코드가 아니라 테이블이 정한다 — 새 카테고리를 넣으면 즉시 통과한다.
 */
class UnknownCategoryException(message: String = "알 수 없는 카테고리입니다.") : SpotQueryException("UNKNOWN_CATEGORY", Classification.BAD_REQUEST, message)

/** TourAPI 장애·타임아웃·오류(XML 오류 포함). DB 폴백 없음. */
class UpstreamUnavailableException(message: String, cause: Throwable? = null) : SpotQueryException("UPSTREAM_UNAVAILABLE", Classification.UNAVAILABLE, message, cause)

/**
 * 카탈로그 자체가 없다. 쿼리 경로이므로 최상위 errors[]로 표면화된다
 * (reactToSpot의 [SpotNotFoundException](userError)과 별개).
 */
class SpotDetailNotFoundException(message: String = "해당 관광지를 찾을 수 없습니다.") : SpotQueryException("SPOT_NOT_FOUND", Classification.NOT_FOUND, message)

/**
 * 카탈로그는 있으나 **활성 언어에 원본이 없다**(SPEC-0029 R-12). 쿼리 경로의 실패다.
 *
 * [SpotDetailNotFoundException]과 반드시 구분한다(AC-12.3) — 언어를 바꾸면 볼 수 있는 장소와
 * 어느 언어에서도 없는 id는 클라이언트가 다르게 안내해야 한다.
 *
 * **정상 경로가 아니라 마지막 방어선이다.** 목록은 전부 활성 언어로 걸러지므로 이 오류에
 * 도달하는 경로는 딥링크·푸시·복원된 초안 같은 우회 진입뿐이다(AC-23.5).
 */
class SpotNotFoundInLocaleQueryException(message: String = "이 언어에서는 볼 수 없는 장소입니다.") : SpotQueryException("SPOT_NOT_FOUND_IN_LOCALE", Classification.NOT_FOUND, message)

/** TourAPI 일일 호출 한도 초과(캐시 미스 시, AC-16). UPSTREAM_UNAVAILABLE과 구분해 클라가 다르게 안내한다. */
class UpstreamRateLimitedException(message: String = "잠시 후 다시 시도해 주세요.", cause: Throwable? = null) : SpotQueryException("UPSTREAM_RATE_LIMITED", Classification.RESOURCE_EXHAUSTED, message, cause)

/**
 * TourAPI **초당** 호출 한도 초과(SPEC-0037). 일일 한도([UpstreamRateLimitedException])와 반드시
 * 구분한다 — 이쪽은 잠시 뒤 재시도하면 풀리는 일시 상태이고, 배치가 둘을 섞으면 초당 한도 한 번에
 * 그날 수집이 통째로 끝난다.
 *
 * 클라이언트에게는 같은 안내(잠시 후 재시도)라 오류 코드를 공유한다.
 */
class UpstreamTooManyRequestsException(message: String = "잠시 후 다시 시도해 주세요.", cause: Throwable? = null) : SpotQueryException("UPSTREAM_RATE_LIMITED", Classification.RESOURCE_EXHAUSTED, message, cause)

/**
 * 지도 뷰포트·혼잡도 조회의 실패(SPEC-0014 §10). 기존 쿼리 오류와 같은 경로로 최상위
 * errors[]에 실린다.
 *
 * **서울 API 장애는 여기에 속하지 않는다** — degraded 표식이 붙은 정상 응답으로 표현한다
 * (R11). 예보 부재·상권 부재도 마찬가지로 오류가 아니다.
 */

/** bbox가 뒤집혔거나 좌표가 유효 범위 밖(R4). */
class InvalidBboxException(message: String = "지도 영역이 올바르지 않습니다.") : SpotQueryException("INVALID_BBOX", Classification.BAD_REQUEST, message)

/** 뷰포트 면적이 상한 초과(R5). 전 지구 bbox로 전체를 긁어가는 것을 막는다. */
class BboxTooLargeException(message: String = "지도를 확대해 주세요.") : SpotQueryException("BBOX_TOO_LARGE", Classification.BAD_REQUEST, message)

/** 줌이 카카오맵 유효 범위 밖(R4). */
class InvalidZoomException(message: String = "지도 확대 수준이 올바르지 않습니다.") : SpotQueryException("INVALID_ZOOM", Classification.BAD_REQUEST, message)

/**
 * 혼잡도 조회에 areaIds도 bbox도 없음(R6). 범위 없는 전체 조회는 일일 쿼터를 한 번에
 * 소진시키므로 거절한다.
 */
class AreaFilterRequiredException(message: String = "조회할 구역 또는 지도 영역이 필요합니다.") : SpotQueryException("AREA_FILTER_REQUIRED", Classification.BAD_REQUEST, message)

/** 121곳에 없는 구역 id(R6). */
class CongestionAreaNotFoundException(message: String = "해당 구역을 찾을 수 없습니다.") : SpotQueryException("CONGESTION_AREA_NOT_FOUND", Classification.NOT_FOUND, message)

/** 구역 id 개수가 상한 초과(R6). 쿼터 보호가 목적이라 클램프가 아니라 거절이다. */
class TooManyAreaIdsException(message: String = "한 번에 조회할 수 있는 구역 수를 초과했습니다.") : SpotQueryException("TOO_MANY_AREA_IDS", Classification.BAD_REQUEST, message)

/**
 * 커서가 유효하지 않다(SPEC-0019 §7). 조작된 값과 **참조 축 이관 이전에 발급된 구 커서**가
 * 모두 여기로 온다.
 *
 * 기존에 예외 메시지("유효하지 않은 커서입니다")로만 존재하던 실패를 코드로 승격한 것이다 —
 * 커서 형식이 바뀌는 이번 변경에서 클라이언트가 이 실패를 **다른 오류와 구분해 분기해야**
 * 하기 때문이다. 클라이언트는 커서를 버리고 첫 페이지부터 다시 요청한다.
 */
class InvalidCursorException(message: String = "유효하지 않은 커서입니다.", cause: Throwable? = null) : SpotQueryException("INVALID_CURSOR", Classification.BAD_REQUEST, message, cause)

/**
 * 검색어가 제출됐으나 트림 후 비었다(SPEC-0033 R6).
 *
 * **인자 생략·null과 구분한다** — 생략은 "필터 없음"이라 오류가 아니고, 빈 문자열 제출은
 * 클라이언트가 막았어야 할 결함이다.
 *
 * `searchSpots`가 같은 코드를 userErrors[]로 내보내는 것과 이름은 같지만 통로가 다르다 —
 * `LikedSpotConnection`에는 payload 래퍼가 없어 최상위 errors[]로 나간다.
 */
class EmptyQueryException(message: String = "검색어를 입력해 주세요.") : SpotQueryException("EMPTY_QUERY", Classification.BAD_REQUEST, message)

/**
 * 검색어가 트림 후 길이 상한을 넘었다(SPEC-0033 R6, POL-035).
 *
 * **메시지에 검색어 원문을 담지 않는다**(POL-013) — 오류 메시지는 클라이언트 로그에 그대로 남는다.
 */
class QueryTooLongException(maxLength: Int) : SpotQueryException("QUERY_TOO_LONG", Classification.BAD_REQUEST, "검색어는 최대 ${maxLength}자까지 입력할 수 있습니다.")

/**
 * 지역 추천 조회의 개수가 0 이하(SPEC-0015 R29).
 *
 * **상한 초과는 이 예외가 아니다** — 상한을 넘는 값은 클램프한다(POL-018). 0 이하만 거절하는
 * 이유는 "0건을 달라"가 의도로 성립하지 않기 때문이며, 조용히 기본값으로 바꾸면 클라이언트의
 * 계산 오류가 드러나지 않는다.
 *
 * 좌표 범위 위반은 기존 [InvalidCoordinatesException]을 그대로 쓴다.
 */
class InvalidLimitException(message: String = "요청 개수가 올바르지 않습니다.") : SpotQueryException("INVALID_LIMIT", Classification.BAD_REQUEST, message)

/** 칸 코드가 존재하지 않는다(SPEC-0030 R7). 시군구 코드를 넣은 경우도 여기로 온다. */
class MapCellNotFoundException(message: String = "해당 지역을 찾을 수 없습니다.") : SpotQueryException("MAP_CELL_NOT_FOUND", Classification.NOT_FOUND, message)

/**
 * 공유 토큰이 없거나 폐기됐다(SPEC-0030 R14).
 *
 * **둘을 구분해 응답하지 않는다.** 구분하면 토큰을 넣어 보며 "존재하지만 폐기됨"을 가려낼 수
 * 있고, 그것만으로도 그 회원이 공유를 했었다는 사실이 새어 나간다.
 */
class ShareLinkNotFoundException(message: String = "더 이상 볼 수 없는 지도입니다.") : SpotQueryException("SHARE_LINK_NOT_FOUND", Classification.NOT_FOUND, message)

/** 차단 관계라 공유 지도를 볼 수 없다(SPEC-0030 R16, POL-041의 양방향 차단). */
class ShareLinkBlockedException(message: String = "볼 수 없는 지도입니다.") : SpotQueryException("SHARE_LINK_BLOCKED", Classification.FAILED_PRECONDITION, message)

/**
 * 자기 자신과 관계를 맺으려 했다(SPEC-0030 확장).
 *
 * 자기 링크를 자기가 여는 것은 정상이지만(자기 지도를 보는 것), 거기서 등록까지 하면
 * 목록에 자기가 나타난다. DB CHECK가 최후 방어선이지만 서비스가 먼저 막아 의미 있는
 * 오류를 준다.
 */
class CannotRelateSelfException(message: String = "자기 자신과는 관계를 맺을 수 없습니다.") : SpotQueryException("CANNOT_RELATE_SELF", Classification.BAD_REQUEST, message)
