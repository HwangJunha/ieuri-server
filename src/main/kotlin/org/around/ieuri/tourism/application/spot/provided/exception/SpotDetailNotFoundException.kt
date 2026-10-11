package org.around.ieuri.tourism.application.spot.provided.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/** 카탈로그 자체가 없다. 쿼리 경로라 최상위 errors[]로 나간다. */
class SpotDetailNotFoundException(message: String = "해당 관광지를 찾을 수 없습니다.") : TourismQueryException("SPOT_NOT_FOUND", Classification.NOT_FOUND, message)
