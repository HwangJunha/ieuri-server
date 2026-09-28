package org.around.ieuri.tourism.application.validation

import org.around.ieuri.tourism.application.exception.InvalidBboxException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.MapBounds

/**
 * 도메인 검증 실패를 오류 코드로 옮긴다. **검증 자체는 도메인이 한다** — 좌표 범위는 [Coordinates]의
 * 불변식이고, 여기서는 그 실패를 `spots`와 같은 코드(INVALID_COORDINATES·RADIUS_TOO_LARGE)로 번역할
 * 뿐이다. 도메인은 오류 코드를 모른다.
 */
fun coordinatesOf(latitude: Double, longitude: Double): Coordinates = try {
    Coordinates(latitude, longitude)
} catch (e: IllegalArgumentException) {
    throw InvalidCoordinatesException(e.message ?: "유효하지 않은 좌표입니다.")
}

/**
 * 뷰포트 사각형을 만든다. 좌표 범위·뒤집힘 판정은 [MapBounds]의 불변식이고, 여기서는 그 실패를
 * `INVALID_BBOX`로 번역할 뿐이다 — 도메인은 오류 코드를 모른다.
 *
 * **조립을 응용 계층에서 하는 이유**: 외부 입력을 도메인 언어로 옮기는 것이 이 계층의 일이라,
 * 인바운드 어댑터가 [MapBounds]를 만들어 넘기면 GraphQL 말고 다른 인바운드가 같은 유스케이스를
 * 부를 때 그 조립과 오류 번역을 각자 반복하게 된다.
 */
fun boundsOf(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): MapBounds = MapBounds.ofOrNull(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng)
    ?: throw InvalidBboxException()

/** 반경 상한은 정책값(설정)이라 도메인 불변식이 아니다 — 상한을 받아 비교만 한다. */
fun requireRadiusWithin(radiusMeters: Int, maxRadiusMeters: Int) {
    if (radiusMeters <= 0) throw InvalidCoordinatesException("반경은 양수여야 합니다.")
    if (radiusMeters > maxRadiusMeters) {
        throw RadiusTooLargeException("반경은 최대 ${maxRadiusMeters}m까지 허용됩니다.")
    }
}
