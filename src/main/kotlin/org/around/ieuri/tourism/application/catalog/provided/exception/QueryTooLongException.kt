package org.around.ieuri.tourism.application.catalog.provided.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/**
 * 검색어가 트림 후 길이 상한을 넘었다(SPEC-0033 R6, POL-035).
 * - 메시지에 검색어 원문을 담지 않는다(POL-013).
 */
class QueryTooLongException(maxLength: Int) : TourismQueryException("QUERY_TOO_LONG", Classification.BAD_REQUEST, "검색어는 최대 ${maxLength}자까지 입력할 수 있습니다.")
