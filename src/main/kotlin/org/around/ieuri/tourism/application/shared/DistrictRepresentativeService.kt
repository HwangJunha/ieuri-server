package org.around.ieuri.tourism.application.shared

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.shared.provided.DistrictRepresentativeUseCase
import org.around.ieuri.tourism.application.shared.required.DistrictRepresentativeRefreshResult
import org.around.ieuri.tourism.application.shared.required.DistrictRepresentativeUpdatePort
import org.around.ieuri.tourism.domain.shared.RepresentativeLocation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 선정과 비우기는 한 트랜잭션이다 — 사이에서 실패하면 문턱 미달 시군구에 낡은 대표 위치가 남는다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class DistrictRepresentativeService(
    private val updatePort: DistrictRepresentativeUpdatePort,
) : DistrictRepresentativeUseCase {

    override fun refreshRepresentativeLocations(cellSizeDegrees: Double, minSpotCount: Int): DistrictRepresentativeRefreshResult {
        val chosen = updatePort.findKoreanSpotPoints().mapNotNull { (code, points) ->
            RepresentativeLocation.choose(points, cellSizeDegrees, minSpotCount)?.let { code to it }
        }.toMap()

        chosen.forEach { (code, choice) -> updatePort.saveRepresentative(code, choice) }
        val cleared = updatePort.clearRepresentativesExcept(chosen.keys)
        return DistrictRepresentativeRefreshResult(selected = chosen.size, cleared = cleared)
    }
}
