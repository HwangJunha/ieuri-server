package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.MapCellDistrictQueryPort
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.MapCellDistrictJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 시군구 → 칸 대응 어댑터(SPEC-0030).
 *
 * 매핑 표(252행)를 그대로 읽는 조회라 공간 함수가 없고, 그래서 파생 쿼리로 끝난다 —
 * 칸 목록·상세가 `center`를 뽑느라 생 SQL인 것([MapCellQueryJdbcAdapter])과 갈린다.
 */
@Component
class MapCellDistrictQueryAdapter(
    private val repository: MapCellDistrictJpaRepository,
) : MapCellDistrictQueryPort {

    @Transactional(readOnly = true)
    override fun findCellCodesByDistrictCodes(districtCodes: Collection<String>): List<String> {
        if (districtCodes.isEmpty()) return emptyList()

        return repository.findAllByDistrictCodeIn(districtCodes)
            .map { it.cellCode }
            .distinct()
            .sorted()
    }
}
