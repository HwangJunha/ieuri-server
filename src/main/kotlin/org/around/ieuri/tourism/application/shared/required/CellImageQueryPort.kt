package org.around.ieuri.tourism.application.shared.required

import org.around.ieuri.tourism.application.shared.provided.dto.CellImageView

/** 칸 대표 이미지의 읽기 포트(SPEC-0030). */
interface CellImageQueryPort {

    /** 모든 칸의 대표 이미지(`display_order = 0`)를 칸 id로 색인한다. 이미지가 없는 칸은 키가 없다. */
    fun findAllRepresentativeImages(): Map<Long, CellImageView>
}
