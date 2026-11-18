package org.around.ieuri.tourism.application.congestion.required

import org.around.ieuri.tourism.application.congestion.provided.dto.CongestionAreaBoundaryView
import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds

/** 혼잡 구역의 정적 메타데이터·경계 조회 아웃바운드 포트. 실시간 값은 여기서 오지 않는다. */
interface CongestionAreaQueryPort {
    /** 전체 구역의 경계와 메타데이터. 이름은 [locale]로 고른다. */
    fun findAllBoundaries(locale: DomainLocale): List<CongestionAreaBoundaryView>

    /** bbox와 폴리곤이 교차하는 구역의 코드. */
    fun findAreaCodesWithin(bounds: MapBounds): List<String>

    /** 주어진 코드 중 실재하는 구역의 이름. 존재하지 않는 코드는 결과에서 빠진다. */
    fun findNamesByCodes(areaCodes: List<String>): Map<String, AreaName>
}
