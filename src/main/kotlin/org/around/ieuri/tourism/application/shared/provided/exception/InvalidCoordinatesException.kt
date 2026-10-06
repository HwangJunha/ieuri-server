package org.around.ieuri.tourism.application.shared.provided.exception

import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.around.ieuri.tourism.domain.shared.SpotQueryException.Classification

/** 위경도 범위 초과/형식 오류. */
class InvalidCoordinatesException(message: String) : SpotQueryException("INVALID_COORDINATES", Classification.BAD_REQUEST, message)
