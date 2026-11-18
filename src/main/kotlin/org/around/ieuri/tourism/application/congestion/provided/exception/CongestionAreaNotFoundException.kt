package org.around.ieuri.tourism.application.congestion.provided.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/** 121곳에 없는 구역 id(R6). */
class CongestionAreaNotFoundException(message: String = "해당 구역을 찾을 수 없습니다.") : TourismQueryException("CONGESTION_AREA_NOT_FOUND", Classification.NOT_FOUND, message)
