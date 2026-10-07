package org.around.ieuri.tourism.application.catalog

import org.around.ieuri.tourism.application.catalog.provided.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.catalog.provided.exception.RadiusTooLargeException
import org.around.ieuri.tourism.domain.shared.Coordinates

/** 좌표를 만든다. [Coordinates]의 불변식 위반은 INVALID_COORDINATES로 번역한다. */
fun coordinatesOf(latitude: Double, longitude: Double): Coordinates = try {
    Coordinates(latitude, longitude)
} catch (e: IllegalArgumentException) {
    throw InvalidCoordinatesException(e.message ?: "유효하지 않은 좌표입니다.")
}

/** 반경이 양수이고 [maxRadiusMeters] 이하인지 확인한다. 위반은 INVALID_COORDINATES·RADIUS_TOO_LARGE다. */
fun requireRadiusWithin(radiusMeters: Int, maxRadiusMeters: Int) {
    if (radiusMeters <= 0) throw InvalidCoordinatesException("반경은 양수여야 합니다.")
    if (radiusMeters > maxRadiusMeters) {
        throw RadiusTooLargeException("반경은 최대 ${maxRadiusMeters}m까지 허용됩니다.")
    }
}
