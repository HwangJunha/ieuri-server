package org.around.ieuri.tourism.application.congestion.provided

import org.around.ieuri.tourism.application.congestion.provided.dto.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.congestion.provided.dto.CulturalEventView
import org.around.ieuri.tourism.application.congestion.provided.dto.SeoulCongestionView
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds

/** 서울 실시간 혼잡도 조회(SPEC-0014). 지도의 다른 최상위 필드와 실패가 서로 전파되지 않는다(R11). */
interface CongestionQueryUseCase {
    /**
     * 지정된 구역의 실시간 혼잡도(R6~R11).
     * - [areaIds]와 [bounds] 중 하나는 필수다 — 둘 다 없으면 `AREA_FILTER_REQUIRED`다.
     * - 둘 다 주어지면 [areaIds]가 우선하고 [bounds]는 무시된다.
     * - 업스트림 장애는 예외가 아니라 `degraded`로 표현된다.
     */
    fun seoulCongestion(
        areaIds: List<String>?,
        bounds: MapBounds?,
        locale: DomainLocale,
    ): SeoulCongestionView

    /** 혼잡도 구역의 폴리곤 경계와 메타데이터(R16·R17). DB 조회다. */
    fun seoulCongestionAreas(locale: DomainLocale): List<CongestionAreaBoundaryView>

    /**
     * bbox에 걸치는 구역의 캐시된 문화행사(R12). 업스트림을 부르지 않는다.
     * - 캐시가 비어 있으면 행사도 비어 있다(혼잡도 조회가 채운다).
     */
    fun cachedEventsWithin(bounds: MapBounds): List<CulturalEventView>
}
