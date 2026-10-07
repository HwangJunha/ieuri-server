package org.around.ieuri.tourism.application.congestion.provided.exception

import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.around.ieuri.tourism.domain.shared.SpotQueryException.Classification

/** 구역 id 개수가 상한 초과(R6). 클램프가 아니라 거절이다. */
class TooManyAreaIdsException(message: String = "한 번에 조회할 수 있는 구역 수를 초과했습니다.") : SpotQueryException("TOO_MANY_AREA_IDS", Classification.BAD_REQUEST, message)
