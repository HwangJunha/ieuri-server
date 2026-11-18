package org.around.ieuri.tourism.application.congestion

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.congestion.provided.CongestionAreaReadUseCase
import org.around.ieuri.tourism.application.congestion.provided.dto.CongestionAreaBoundaryView
import org.around.ieuri.tourism.application.congestion.provided.exception.AreaFilterRequiredException
import org.around.ieuri.tourism.application.congestion.provided.exception.CongestionAreaNotFoundException
import org.around.ieuri.tourism.application.congestion.provided.exception.TooManyAreaIdsException
import org.around.ieuri.tourism.application.congestion.required.CongestionAreaQueryPort
import org.around.ieuri.tourism.domain.shared.AreaName
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapBounds
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 혼잡도 구역의 DB 조회 구현. 캐시·업스트림은 모른다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class CongestionAreaReadService(
    private val congestionAreaQueryPort: CongestionAreaQueryPort,
    private val openDataPolicy: SeoulOpenDataPolicy,
) : CongestionAreaReadUseCase {

    override fun requestedAreas(areaIds: List<String>?, bounds: MapBounds?): Map<String, AreaName> {
        if (areaIds != null) {
            if (areaIds.size > openDataPolicy.maxAreaIds) {
                throw TooManyAreaIdsException(
                    "한 번에 조회할 수 있는 구역은 ${openDataPolicy.maxAreaIds}곳까지입니다.",
                )
            }
            val distinct = areaIds.distinct()
            if (distinct.isEmpty()) return emptyMap()
            // 존재하지 않는 코드는 무시하지 않고 거절한다.
            val known = congestionAreaQueryPort.findNamesByCodes(distinct)
            val unknown = distinct.filterNot(known::containsKey)
            if (unknown.isNotEmpty()) {
                throw CongestionAreaNotFoundException("해당 구역을 찾을 수 없습니다: ${unknown.joinToString()}")
            }
            return inOrder(distinct, known)
        }
        if (bounds != null) {
            val codes = congestionAreaQueryPort.findAreaCodesWithin(bounds).take(openDataPolicy.maxAreaIds)
            if (codes.isEmpty()) return emptyMap()
            return inOrder(codes, congestionAreaQueryPort.findNamesByCodes(codes))
        }
        throw AreaFilterRequiredException("조회할 구역 또는 지도 영역이 필요합니다.")
    }

    override fun areaCodesWithin(bounds: MapBounds): List<String> = congestionAreaQueryPort.findAreaCodesWithin(bounds)

    override fun seoulCongestionAreas(locale: DomainLocale): List<CongestionAreaBoundaryView> = congestionAreaQueryPort.findAllBoundaries(locale)

    /** 요청 순서를 지킨다. 이름이 없는 코드는 빠진다. */
    private fun inOrder(codes: List<String>, names: Map<String, AreaName>): Map<String, AreaName> = codes.mapNotNull { code -> names[code]?.let { code to it } }.toMap()
}
