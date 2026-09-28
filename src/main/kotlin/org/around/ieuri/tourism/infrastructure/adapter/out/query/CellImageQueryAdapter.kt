package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.query.CellImageQueryPort
import org.around.ieuri.tourism.application.query.CellImageView
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toCellImageView
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.MapCellImageJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 칸 대표 이미지 조회 어댑터(SPEC-0030 R9·R10).
 *
 * 161행짜리 조회이고 조건이 `display_order = 0` 하나라 파생 쿼리로 끝난다 —
 * 공간 함수도 집계도 없어 생 SQL이 필요하지 않다.
 *
 * **이미지가 없는 칸은 결과 Map에 키가 없다** — 그 칸도 조회에서 빠지지 않으며 이미지 필드만
 * null이 된다(R10).
 */
@Component
class CellImageQueryAdapter(
    private val repository: MapCellImageJpaRepository,
) : CellImageQueryPort {

    @Transactional(readOnly = true)
    override fun findAllRepresentativeImages(): Map<Long, CellImageView> = repository.findAllByDisplayOrder(REPRESENTATIVE_ORDER)
        .associate { it.cellId to it.toCellImageView() }

    private companion object {
        /** 0이 대표다(V77). */
        const val REPRESENTATIVE_ORDER = 0
    }
}
