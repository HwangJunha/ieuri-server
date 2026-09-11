package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.domain.vo.Coordinates

/**
 * 좌표·반경 검증. `Query.spots`(SpotQueryService)와 같은 규칙·같은 오류 코드다 —
 * `Location`의 두 보기가 그 규칙을 그대로 받도록 한 곳에 둔다(SPEC-0035 AC-1.2·AC-2.1).
 */
internal fun validatedCenter(latitude: Double, longitude: Double): Coordinates =
    try {
        Coordinates(latitude, longitude)
    } catch (e: IllegalArgumentException) {
        throw InvalidCoordinatesException(e.message ?: "유효하지 않은 좌표입니다.")
    }

internal fun validateRadius(radiusMeters: Int, maxRadiusMeters: Int) {
    if (radiusMeters <= 0) throw InvalidCoordinatesException("반경은 양수여야 합니다.")
    if (radiusMeters > maxRadiusMeters) {
        throw RadiusTooLargeException("반경은 최대 ${maxRadiusMeters}m까지 허용됩니다.")
    }
}
