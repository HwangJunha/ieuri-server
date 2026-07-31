package org.around.ieuri.tourism.domain.vo

/**
 * 위경도 좌표(WGS84). 생성 시점에 유효 범위를 강제한다(위도 -90..90, 경도 -180..180).
 * 범위를 벗어나면 IllegalArgumentException — 인바운드에서 INVALID_COORDINATES로 매핑된다(AC-6).
 *
 * 개인정보 보호(POL-013): 이 값은 조회에만 쓰고 저장·로깅하지 않는다. toString도 좌표를 노출하지 않는다.
 */
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "위도는 -90~90 범위여야 합니다." }
        require(longitude in -180.0..180.0) { "경도는 -180~180 범위여야 합니다." }
    }

    /** 로그·에러에 원좌표가 새지 않도록 마스킹한다(AC-23). */
    override fun toString(): String = "Coordinates(masked)"
}
