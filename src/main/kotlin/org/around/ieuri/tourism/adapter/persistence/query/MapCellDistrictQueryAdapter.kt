package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.adapter.persistence.table.MapCellDistrictTable
import org.around.ieuri.tourism.application.footprint.required.MapCellDistrictQueryPort
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

/** 시군구 → 칸 대응 어댑터(SPEC-0030). 칸 id 오름차순이다. */
@Component
class MapCellDistrictQueryAdapter : MapCellDistrictQueryPort {
    override fun findCellIdsByDistrictCodes(districtCodes: Collection<String>): List<Long> {
        if (districtCodes.isEmpty()) return emptyList()

        return MapCellDistrictTable.select(MapCellDistrictTable.cellId)
            .where { MapCellDistrictTable.districtCode inList districtCodes }
            .map { it[MapCellDistrictTable.cellId] }
            .distinct()
            .sorted()
    }
}
