package org.around.ieuri.tourism.application.catalog.provided.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/** 위경도 범위 초과/형식 오류. */
class InvalidCoordinatesException(message: String) : TourismQueryException("INVALID_COORDINATES", Classification.BAD_REQUEST, message)
