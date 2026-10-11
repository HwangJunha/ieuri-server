package org.around.ieuri.tourism.application.catalog.provided.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/** 줌이 카카오맵 유효 범위 밖(R4). */
class InvalidZoomException(message: String = "지도 확대 수준이 올바르지 않습니다.") : TourismQueryException("INVALID_ZOOM", Classification.BAD_REQUEST, message)
