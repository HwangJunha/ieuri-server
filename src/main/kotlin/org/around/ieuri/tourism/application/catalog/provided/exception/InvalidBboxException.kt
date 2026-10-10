package org.around.ieuri.tourism.application.catalog.provided.exception

import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.around.ieuri.tourism.domain.shared.SpotQueryException.Classification

/** bbox가 뒤집혔거나 좌표가 유효 범위 밖(R4). */
class InvalidBboxException(message: String = "지도 영역이 올바르지 않습니다.") : SpotQueryException("INVALID_BBOX", Classification.BAD_REQUEST, message)
