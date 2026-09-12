package org.around.ieuri.tourism.application.validation

import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.domain.vo.Coordinates

/**
 * 도메인 검증 실패를 오류 코드로 옮긴다. **검증 자체는 도메인이 한다** — 좌표 범위는 [Coordinates]의
 * 불변식이고, 여기서는 그 실패를 `spots`와 같은 코드(INVALID_COORDINATES·RADIUS_TOO_LARGE)로 번역할
 * 뿐이다. 도메인은 오류 코드를 모른다.
 */
fun coordinatesOf(latitude: Double, longitude: Double): Coordinates =
    try {
        Coordinates(latitude, longitude)
    } catch (e: IllegalArgumentException) {
        throw InvalidCoordinatesException(e.message ?: "유효하지 않은 좌표입니다.")
    }

/** 반경 상한은 정책값(설정)이라 도메인 불변식이 아니다 — 상한을 받아 비교만 한다. */
fun requireRadiusWithin(radiusMeters: Int, maxRadiusMeters: Int) {
    if (radiusMeters <= 0) throw InvalidCoordinatesException("반경은 양수여야 합니다.")
    if (radiusMeters > maxRadiusMeters) {
        throw RadiusTooLargeException("반경은 최대 ${maxRadiusMeters}m까지 허용됩니다.")
    }
}
