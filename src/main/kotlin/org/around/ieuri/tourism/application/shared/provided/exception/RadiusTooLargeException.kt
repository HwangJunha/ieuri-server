package org.around.ieuri.tourism.application.shared.provided.exception

import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.around.ieuri.tourism.domain.shared.SpotQueryException.Classification

/** 반경 상한(100,000m) 초과. */
class RadiusTooLargeException(message: String) : SpotQueryException("RADIUS_TOO_LARGE", Classification.BAD_REQUEST, message)
