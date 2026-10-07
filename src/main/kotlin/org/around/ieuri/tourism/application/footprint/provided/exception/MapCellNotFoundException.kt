package org.around.ieuri.tourism.application.footprint.provided.exception

import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.around.ieuri.tourism.domain.shared.SpotQueryException.Classification

/** 칸 코드가 존재하지 않는다(SPEC-0030 R7). 시군구 코드를 넣은 경우도 여기로 온다. */
class MapCellNotFoundException(message: String = "해당 지역을 찾을 수 없습니다.") : SpotQueryException("MAP_CELL_NOT_FOUND", Classification.NOT_FOUND, message)
