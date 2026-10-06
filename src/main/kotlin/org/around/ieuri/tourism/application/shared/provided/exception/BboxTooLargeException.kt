package org.around.ieuri.tourism.application.shared.provided.exception

import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.around.ieuri.tourism.domain.shared.SpotQueryException.Classification

/** 뷰포트 면적이 상한 초과(R5). */
class BboxTooLargeException(message: String = "지도를 확대해 주세요.") : SpotQueryException("BBOX_TOO_LARGE", Classification.BAD_REQUEST, message)
