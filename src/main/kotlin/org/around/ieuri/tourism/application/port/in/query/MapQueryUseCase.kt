package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.tourism.application.query.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.query.MapViewportQuery
import org.around.ieuri.tourism.application.query.MapViewportView
import org.around.ieuri.tourism.application.query.SeoulCongestionView
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapBounds

/**
 * 지도 화면 조회 유스케이스(SPEC-0014). 언어는 요청 스코프에서 결정돼 명시 파라미터로
 * 전달된다 — 리졸버가 Accept-Language 헤더에서 읽어 넘기며 application은 서블릿·헤더를 모른다.
 *
 * 세 메서드가 **별개 최상위 필드**에 대응한다. 한쪽의 실패가 다른 쪽에 전파되지 않게
 * 하려는 설계이며(R11), 특히 [seoulCongestion]의 업스트림 장애가 [mapViewport]를
 * 건드리지 않는다.
 */
interface MapQueryUseCase {
    /**
     * 뷰포트 안의 spot·클러스터·문화행사 마커(R1~R3, R12).
     *
     * 줌이 임계값 미만이면 클러스터가, 이상이면 개별 마커가 채워진다 — 둘은 상호배타적이다.
     * 상한을 넘으면 잘림 플래그가 선다.
     */
    fun mapViewport(query: MapViewportQuery, locale: DomainLocale): MapViewportView

    /**
     * 지정된 구역의 실시간 혼잡도(R6~R11).
     *
     * [areaIds]와 [bounds] 중 하나는 필수다 — 둘 다 없으면 `AREA_FILTER_REQUIRED`다.
     * 둘 다 주어지면 [areaIds]가 우선하고 [bounds]는 무시된다.
     *
     * 업스트림 장애는 예외가 아니라 `degraded`로 표현된다.
     */
    fun seoulCongestion(
        areaIds: List<String>?,
        bounds: MapBounds?,
        locale: DomainLocale,
    ): SeoulCongestionView

    /**
     * 121곳의 폴리곤 경계와 메타데이터(R16·R17). 실시간 의존이 없는 순수 DB 조회다.
     */
    fun seoulCongestionAreas(locale: DomainLocale): List<CongestionAreaBoundaryView>
}
