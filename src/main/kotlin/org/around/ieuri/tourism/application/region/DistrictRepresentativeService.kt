package org.around.ieuri.tourism.application.region

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.region.provided.DistrictRepresentativeUseCase
import org.around.ieuri.tourism.application.region.provided.dto.DistrictRepresentativeRefreshResult
import org.around.ieuri.tourism.application.region.required.DistrictRepository
import org.around.ieuri.tourism.application.region.required.RepresentativePointQueryPort
import org.around.ieuri.tourism.domain.region.RepresentativeLocation
import org.around.ieuri.tourism.domain.region.RepresentativeRelocation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 선정과 비우기는 한 트랜잭션이다 — 사이에서 실패하면 문턱 미달 시군구에 낡은 대표 위치가 남는다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class DistrictRepresentativeService(
    private val pointQueryPort: RepresentativePointQueryPort,
    private val districtRepository: DistrictRepository,
) : DistrictRepresentativeUseCase {

    override fun refreshRepresentativeLocations(cellSizeDegrees: Double, minSpotCount: Int): DistrictRepresentativeRefreshResult {
        val chosen = pointQueryPort.findKoreanSpotPoints(cellSizeDegrees).mapNotNull { (code, points) ->
            RepresentativeLocation.choose(points, minSpotCount)?.let { code to it }
        }.toMap()

        var selected = 0
        var cleared = 0
        districtRepository.findAll().forEach { district ->
            when (val relocation = district.relocateRepresentative(chosen[district.code])) {
                is RepresentativeRelocation.Assign -> {
                    districtRepository.saveRepresentative(district.code, relocation.choice)
                    selected++
                }

                RepresentativeRelocation.Clear -> {
                    districtRepository.clearRepresentative(district.code)
                    cleared++
                }

                RepresentativeRelocation.Unchanged -> Unit
            }
        }
        return DistrictRepresentativeRefreshResult(selected = selected, cleared = cleared)
    }
}
