package org.around.ieuri.tourism.domain.vo

/**
 * 지도 뷰포트 사각형(SPEC-0014 R1·R4·R5). 경계선에 걸친 대상은 포함한다.
 *
 * 날짜변경선을 넘는 bbox(`minLng > maxLng`)는 지원하지 않는다 — 서비스 대상이 한국이라
 * 정상 사용에서 발생하지 않고, 지원하면 질의가 둘로 쪼개진다. 뒤집힌 값으로 보아 거절한다.
 *
 * 면적은 위경도 **도(degree) 곱**이며 제곱미터가 아니다. 정확한 구면 면적이 필요한 것이
 * 아니라 "전 지구를 한 번에 긁는 요청"만 막으면 되고, 도 단위는 위도에 따라 실제 면적이
 * 달라지지만 그 오차가 이 목적에는 문제가 되지 않는다.
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

    companion object {
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0

        /**
         * 좌표 범위와 뒤집힘을 검증한다. 유효하지 않으면 null — 호출자가 오류 코드를 정한다
         * (도메인은 GraphQL 오류 코드를 모른다).
         *
         * `min >= max`를 거절한다: 같은 값이면 넓이 0의 선분이라 마커가 나올 수 없다.
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
