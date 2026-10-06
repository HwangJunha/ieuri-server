package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.adapter.persistence.mapper.toCellImageView
import org.around.ieuri.tourism.adapter.persistence.table.MapCellImageTable
import org.around.ieuri.tourism.application.shared.provided.dto.CellImageView
import org.around.ieuri.tourism.application.shared.required.CellImageQueryPort
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

/**
 * 칸 대표 이미지 조회 어댑터(SPEC-0030 R9·R10).
 * - 이미지가 없는 칸은 결과 Map에 키가 없다(R10).
 */
@Component
class CellImageQueryAdapter : CellImageQueryPort {
    override fun findAllRepresentativeImages(): Map<Long, CellImageView> = MapCellImageTable.selectAll()
        .where { MapCellImageTable.displayOrder eq REPRESENTATIVE_ORDER }
        .associate { it[MapCellImageTable.cellId] to it.toCellImageView() }

    private companion object {
        /** 0이 대표다. */
        const val REPRESENTATIVE_ORDER = 0
    }
}
