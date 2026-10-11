package org.around.ieuri.tourism.application.shared.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/** TourAPI 장애·타임아웃·오류(XML 오류 포함). DB 폴백 없음. */
class UpstreamUnavailableException(message: String, cause: Throwable? = null) : TourismQueryException("UPSTREAM_UNAVAILABLE", Classification.UNAVAILABLE, message, cause)
