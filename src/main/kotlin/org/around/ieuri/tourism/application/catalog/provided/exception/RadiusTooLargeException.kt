package org.around.ieuri.tourism.application.catalog.provided.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/** 반경 상한(100,000m) 초과. */
class RadiusTooLargeException(message: String) : TourismQueryException("RADIUS_TOO_LARGE", Classification.BAD_REQUEST, message)
