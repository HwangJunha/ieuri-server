package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.tourism.application.query.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.query.MapViewportQuery
import org.around.ieuri.tourism.application.query.MapViewportView
import org.around.ieuri.tourism.application.query.SeoulCongestionView
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapBounds

/**
 * 지도 화면 조회 유스케이스(SPEC-0014).
 * - 세 메서드는 별개 최상위 필드라 한쪽의 실패가 다른 쪽에 전파되지 않는다(R11).
 */
interface MapQueryUseCase {
    /**
     * 뷰포트 안의 spot·문화행사 마커(R1~R3, R12).
     * - 상한을 넘으면 잘리고 잘림 플래그가 선다.
     */
    fun mapViewport(query: MapViewportQuery, locale: DomainLocale): MapViewportView

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
}
