package org.around.ieuri.tourism.application.congestion.provided

import org.around.ieuri.tourism.application.congestion.provided.dto.CongestionAreaBoundaryView
import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds

/** 혼잡도 구역의 DB 조회. 호출마다 읽기 전용 트랜잭션 하나다 — 캐시·업스트림을 끼는 [CongestionQueryUseCase]가 단계마다 부른다. */
interface CongestionAreaReadUseCase {
    /**
     * 조회 대상 구역 코드 → 이름, 요청 순서대로. [areaIds]가 우선하고, 없으면 bbox에 걸치는 구역이다(상한까지).
     * - 둘 다 없으면 AREA_FILTER_REQUIRED, 상한 초과는 TOO_MANY_AREA_IDS, 없는 코드는 CONGESTION_AREA_NOT_FOUND다.
     * - 빈 목록(`areaIds: []`)이나 걸치는 구역이 없는 bbox는 오류가 아니라 빈 결과다.
     */
    fun requestedAreas(areaIds: List<String>?, bounds: MapBounds?): Map<String, AreaName>

    /** bbox에 걸치는 구역 코드. */
    fun areaCodesWithin(bounds: MapBounds): List<String>

    /** 혼잡도 구역의 폴리곤 경계와 메타데이터(R16·R17). */
    fun seoulCongestionAreas(locale: DomainLocale): List<CongestionAreaBoundaryView>
}
