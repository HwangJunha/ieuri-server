package org.around.ieuri.community.domain.vo

/**
 * 글이 붙은 장소의 좌표. tourism에서 복사해 온 값이다.
 *
 * 매번 tourism에 반경 안 spot 목록을 물으면 **그 목록의 상한이 곧 피드의 한계**가 된다
 * (실측: 기본 반경 20km에 spot 12,000개, 상한 2,000). 좌표를 글 쪽에 복사해 두면
 * 반경 조건을 글 조회에 직접 걸 수 있어 상한이 사라진다.
 */
data class SpotPoint(val longitude: Double, val latitude: Double) {
    init {
        require(longitude in -180.0..180.0) { "경도는 -180~180 범위여야 합니다." }
        require(latitude in -90.0..90.0) { "위도는 -90~90 범위여야 합니다." }
    }
}
