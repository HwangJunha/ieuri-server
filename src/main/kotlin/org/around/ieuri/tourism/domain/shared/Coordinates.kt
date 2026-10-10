package org.around.ieuri.tourism.domain.shared

/**
 * 위경도 좌표(WGS84). 범위(위도 -90..90, 경도 -180..180) 밖이면 IllegalArgumentException이다.
 * - 저장·로깅하지 않는다(POL-013). toString도 좌표를 노출하지 않는다.
 */
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "위도는 -90~90 범위여야 합니다." }
        require(longitude in -180.0..180.0) { "경도는 -180~180 범위여야 합니다." }
    }

    /** 로그·에러에 원좌표가 새지 않도록 마스킹한다. */
    override fun toString(): String = "Coordinates(masked)"
}
