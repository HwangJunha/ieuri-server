package org.around.ieuri.tourism.application.shared.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/** TourAPI 일일 호출 한도 초과(AC-16). UPSTREAM_UNAVAILABLE과 구분해 클라가 다르게 안내한다. */
class UpstreamRateLimitedException(message: String = "잠시 후 다시 시도해 주세요.", cause: Throwable? = null) : TourismQueryException("UPSTREAM_RATE_LIMITED", Classification.RESOURCE_EXHAUSTED, message, cause)
