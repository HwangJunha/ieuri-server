package org.around.ieuri.tourism.application.catalog.provided.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/** 뷰포트 면적이 상한 초과(R5). */
class BboxTooLargeException(message: String = "지도를 확대해 주세요.") : TourismQueryException("BBOX_TOO_LARGE", Classification.BAD_REQUEST, message)
