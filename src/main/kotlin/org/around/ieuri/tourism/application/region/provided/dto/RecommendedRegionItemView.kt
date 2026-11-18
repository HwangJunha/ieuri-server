package org.around.ieuri.tourism.application.region.provided.dto

import org.around.ieuri.tourism.domain.shared.Coordinates

/**
 * 추천 시군구 1건.
 * - [scores]에는 산출 가능했던 항목만 들어가며 부재 항목을 0점으로 채우지 않는다(POL-043).
 * - 총점은 정렬 키로만 쓰고 내보내지 않는다.
 */
data class RecommendedRegionItemView(
    val code: String,
    val name: String,
    val provinceName: String,
    val scores: List<RegionScoreView>,
    val scoreFactorCount: Int,
    val center: Coordinates,
)
