package org.around.ieuri.community.domain.post

/** 글이 붙은 장소의 좌표. tourism에서 복사해 온 값이다. */
data class SpotPoint(val longitude: Double, val latitude: Double) {
    init {
        require(longitude in -180.0..180.0) { "경도는 -180~180 범위여야 합니다." }
        require(latitude in -90.0..90.0) { "위도는 -90~90 범위여야 합니다." }
    }
}
