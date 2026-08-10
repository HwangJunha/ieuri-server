package org.around.ieuri.tourism.domain.vo

/**
 * 거리 점수 산출(SPEC-0015 R13 · 설계 §3.3).
 *
 * 감쇠 함수는 SPEC-0013 검색과 같은 형태(`1 / (1 + 거리 / 반감거리)`)를 쓰되 **계수는 그대로
 * 가져오지 않는다.** 검색은 spot 단위 수 km 규모였고 여기는 시군구 단위 수십 km 규모다.
 * [halfLifeMeters]는 설정에서 주입되는 미결값이다(POL-044).
 *
 * 거리는 필터가 아니라 점수 항목이다 — 반경으로 잘라내지 않으므로 아무리 멀어도 0에
 * 수렴할 뿐 후보에서 빠지지는 않는다.
 */
@JvmInline
value class DistanceScoring(val halfLifeMeters: Double) {

    init {
        require(halfLifeMeters > 0.0) { "거리 반감 거리는 양수여야 합니다: $halfLifeMeters" }
    }

    /** 사용자 위치와 지역 대표 좌표 사이의 거리 점수. 같은 좌표면 100이다(R13). */
    fun score(origin: Coordinates, regionCenter: Coordinates): RegionScore =
        score(Distance.between(origin, regionCenter))

    /** 반감 거리에서 계수가 0.5가 되어 50점이 된다. 가까울수록 높다. */
    fun score(distance: Distance): RegionScore {
        val decay = 1.0 / (1.0 + distance.meters / halfLifeMeters)
        return RegionScore(RegionScoreFactor.DISTANCE, decay * RegionScore.MAX_VALUE)
    }
}
