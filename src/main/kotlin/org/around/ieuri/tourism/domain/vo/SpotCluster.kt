package org.around.ieuri.tourism.domain.vo

/**
 * 저줌에서 묶인 spot 클러스터 1개(SPEC-0014 R2).
 *
 * 중심은 셀 중심이 아니라 **포함된 spot 좌표의 평균**이다 — 셀 중심을 쓰면 마커가 실제
 * 밀집 위치에서 벗어난다.
 *
 * [dominantCategory]는 **과반(50% 초과)을 차지하는 카테고리**이며 과반이 없으면 null이다.
 * 판정은 집계와 함께 SQL에서 이뤄진다(`MapSpatialQueryAdapter`) — 카테고리 목록을
 * 애플리케이션으로 올려 세면 저줌 한 셀에 수천 건이 실려 온다.
 */
data class SpotCluster(
    val centerLng: Double,
    val centerLat: Double,
    val count: Int,
    val dominantCategory: SpotCategory?,
)
