package org.around.ieuri.tourism.application.region.provided.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/**
 * 지역 추천 조회의 개수가 0 이하(SPEC-0015 R29).
 * - 상한 초과는 거절이 아니라 클램프한다(POL-018).
 */
class InvalidLimitException(message: String = "요청 개수가 올바르지 않습니다.") : TourismQueryException("INVALID_LIMIT", Classification.BAD_REQUEST, message)
