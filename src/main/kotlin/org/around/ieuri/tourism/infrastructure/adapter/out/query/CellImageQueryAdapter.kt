package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.CellImageQueryPort
import org.around.ieuri.tourism.application.query.CellImageView
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toCellImageView
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.MapCellImageJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 칸 대표 이미지 조회 어댑터(SPEC-0030 R9·R10). 파생 쿼리다.
 * - 이미지가 없는 칸은 결과 Map에 키가 없다(R10).
 */
@Component
class CellImageQueryAdapter(
    private val repository: MapCellImageJpaRepository,
) : CellImageQueryPort {

    @Transactional(readOnly = true)
    override fun findAllRepresentativeImages(): Map<Long, CellImageView> = repository.findAllByDisplayOrder(REPRESENTATIVE_ORDER)
        .associate { it.cellId to it.toCellImageView() }

    private companion object {
        /** 0이 대표다. */
        const val REPRESENTATIVE_ORDER = 0
    }
}
