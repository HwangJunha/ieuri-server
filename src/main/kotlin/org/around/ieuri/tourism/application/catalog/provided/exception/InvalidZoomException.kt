package org.around.ieuri.tourism.application.catalog.provided.exception

import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.around.ieuri.tourism.domain.shared.SpotQueryException.Classification

/** 줌이 카카오맵 유효 범위 밖(R4). */
class InvalidZoomException(message: String = "지도 확대 수준이 올바르지 않습니다.") : SpotQueryException("INVALID_ZOOM", Classification.BAD_REQUEST, message)
