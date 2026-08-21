package org.around.ieuri.tourism.application.policy

/**
 * 전국 지도 조회 정책값(설정에서 주입, POL-044).
 *
 * @param maxCellSpotPageSize 칸 상세 spot 목록의 first 상한(= 50). 초과는 클램프다(POL-018).
 *   기존 커넥션 관례(`likedSpots`, `ongoingFestivals`)와 맞췄으나 **다른 계약이라 노브가 따로다**.
 */
data class NationwideMapPolicy(
    val maxCellSpotPageSize: Int,
)
