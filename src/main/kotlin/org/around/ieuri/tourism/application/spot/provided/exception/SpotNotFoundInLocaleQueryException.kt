package org.around.ieuri.tourism.application.spot.provided.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/**
 * 카탈로그는 있으나 활성 언어에 원본이 없다(SPEC-0029 R-12). 쿼리 경로의 실패다.
 * - [SpotDetailNotFoundException]과 구분한다(AC-12.3).
 * - 목록은 활성 언어로 걸러지므로 딥링크·푸시 같은 우회 진입에서만 도달한다.
 */
class SpotNotFoundInLocaleQueryException(message: String = "이 언어에서는 볼 수 없는 장소입니다.") : TourismQueryException("SPOT_NOT_FOUND_IN_LOCALE", Classification.NOT_FOUND, message)
