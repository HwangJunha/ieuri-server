package org.around.ieuri.tourism.application.shared.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/**
 * TourAPI 초당 호출 한도 초과(SPEC-0037).
 * - 일일 한도([UpstreamRateLimitedException])와 달리 잠시 뒤 재시도하면 풀린다 — 배치는 둘을 섞지 않는다.
 * - 클라이언트에게는 같은 오류 코드를 낸다.
 */
class UpstreamTooManyRequestsException(message: String = "잠시 후 다시 시도해 주세요.", cause: Throwable? = null) : TourismQueryException("UPSTREAM_RATE_LIMITED", Classification.RESOURCE_EXHAUSTED, message, cause)
