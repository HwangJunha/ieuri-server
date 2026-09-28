package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.MapCellDistrictQueryPort
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.MapCellDistrictJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 시군구 → 칸 대응 어댑터(SPEC-0030). 매핑 표를 읽는 파생 쿼리다. */
@Component
class MapCellDistrictQueryAdapter(
    private val repository: MapCellDistrictJpaRepository,
) : MapCellDistrictQueryPort {

    @Transactional(readOnly = true)
    override fun findCellIdsByDistrictCodes(districtCodes: Collection<String>): List<Long> {
        if (districtCodes.isEmpty()) return emptyList()

        return repository.findAllByDistrictCodeIn(districtCodes)
            .map { it.cellId }
            .distinct()
            .sorted()
    }
}
