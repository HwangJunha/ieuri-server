package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.application.query.CellImageView

/** 칸 대표 이미지의 읽기 포트(SPEC-0030 R9·R10). */
interface CellImageQueryPort {

    /**
     * 모든 칸의 대표 이미지(`display_order = 0`), 칸 코드로 색인한 결과.
     *
     * **이미지가 없는 칸은 결과에 키가 없다** — 그 칸도 조회에서 빠지지 않으며 이미지 필드만
     * null이 된다(R10).
     *
     * 칸이 161개로 고정이고 칸 목록이 전량을 한 번에 그리므로 부분 조회를 두지 않는다.
     */
    fun findAllRepresentativeImages(): Map<String, CellImageView>
}
