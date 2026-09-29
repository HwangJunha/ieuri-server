package org.around.ieuri.tourism.domain.vo

/**
 * 지도 뷰포트 사각형(SPEC-0014 R1·R4·R5). 경계선에 걸친 대상은 포함한다.
 * - 날짜변경선을 넘는 bbox(`minLng > maxLng`)는 뒤집힌 값으로 보아 거절한다.
 * - 면적은 위경도 도(degree) 곱이며 제곱미터가 아니다.
 */
data class MapBounds(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
) {
    /** 위경도 도 단위 면적. 상한 비교에만 쓴다. */
    val areaInSquareDegrees: Double
        get() = (maxLat - minLat) * (maxLng - minLng)

    /** 사각형의 중심점(위경도 평균, SPEC-0036). 추천 점수의 거리 기준점이며 사용자 위치가 아니다. */
    val center: Coordinates
        get() = Coordinates(latitude = (minLat + maxLat) / 2.0, longitude = (minLng + maxLng) / 2.0)

    companion object {
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0

        /**
         * 좌표 범위와 뒤집힘을 검증한다. 유효하지 않으면 null — 호출자가 오류 코드를 정한다.
         * - `min >= max`를 거절한다.
         */
        fun ofOrNull(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): MapBounds? {
            if (!minLat.isFinite() || !minLng.isFinite() || !maxLat.isFinite() || !maxLng.isFinite()) return null
            if (minLat < MIN_LATITUDE || maxLat > MAX_LATITUDE) return null
            if (minLng < MIN_LONGITUDE || maxLng > MAX_LONGITUDE) return null
            if (minLat >= maxLat || minLng >= maxLng) return null
            return MapBounds(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng)
        }
    }
}
