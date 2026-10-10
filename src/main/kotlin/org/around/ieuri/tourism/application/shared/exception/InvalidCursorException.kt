package org.around.ieuri.tourism.application.shared.exception

import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.around.ieuri.tourism.domain.shared.SpotQueryException.Classification

/**
 * 커서가 유효하지 않다(SPEC-0019). 조작된 값과 참조 축 이관 이전의 구 커서가 모두 여기로 온다.
 * - 클라이언트는 커서를 버리고 첫 페이지부터 다시 요청한다.
 */
class InvalidCursorException(message: String = "유효하지 않은 커서입니다.", cause: Throwable? = null) : SpotQueryException("INVALID_CURSOR", Classification.BAD_REQUEST, message, cause)
