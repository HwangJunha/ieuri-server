package org.around.ieuri.tourism.application.category.provided.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/**
 * `category` 테이블에 없는 카테고리 코드로 상세 하위 필드를 물었다(SPEC-0020).
 * - 필터에는 이 검증이 없다 — 없는 코드로 거르면 0건일 뿐이다.
 */
class UnknownCategoryException(message: String = "알 수 없는 카테고리입니다.") : TourismQueryException("UNKNOWN_CATEGORY", Classification.BAD_REQUEST, message)
