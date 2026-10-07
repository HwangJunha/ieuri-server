package org.around.ieuri.tourism.application.congestion.provided.exception

import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.around.ieuri.tourism.domain.shared.SpotQueryException.Classification

/** 혼잡도 조회에 areaIds도 bbox도 없음(R6). 범위 없는 전체 조회는 거절한다. */
class AreaFilterRequiredException(message: String = "조회할 구역 또는 지도 영역이 필요합니다.") : SpotQueryException("AREA_FILTER_REQUIRED", Classification.BAD_REQUEST, message)
