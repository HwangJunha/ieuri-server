package org.around.ieuri.tourism.application.region.required

import org.around.ieuri.tourism.domain.region.RepresentativeLocation

/** 시군구 대표 위치를 고를 재료 조회(SPEC-0015 · V46). */
interface RepresentativePointQueryPort {
    /** 시군구 코드 → 경계 안 KO 원본과 그 격자 순위. 경계선 위의 원본도 그 시군구에 든다. */
    fun findKoreanSpotPoints(cellSizeDegrees: Double): Map<String, List<RepresentativeLocation.Point>>
}
