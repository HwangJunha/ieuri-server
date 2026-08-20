package org.around.ieuri.tourism.application.policy

/**
 * 전국 지도 조회 정책값(설정에서 주입, POL-044).
 *
 * @param maxResolveCoordinates `resolveDistricts` 한 번에 받는 좌표 상한(= 500).
 *   **초과는 클램프가 아니라 거절이다**(TOO_MANY_COORDINATES) — 잘라서 판정하면 클라이언트가
 *   전부 처리된 줄 알고 나머지를 다시 보내지 않아 그 지역이 조용히 미방문으로 남는다.
 *   다른 상한들이 클램프인 것과 다르며, 그 차이가 이 값의 요지다.
 * @param maxCellSpotPageSize 칸 상세 spot 목록의 first 상한(= 50). 초과는 클램프다(POL-018).
 *   기존 커넥션 관례(`likedSpots`, `ongoingFestivals`)와 맞췄으나 **다른 계약이라 노브가 따로다**.
 */
data class NationwideMapPolicy(
    val maxResolveCoordinates: Int,
    val maxCellSpotPageSize: Int,
)
