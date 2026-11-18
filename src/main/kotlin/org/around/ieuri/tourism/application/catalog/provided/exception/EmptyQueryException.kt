package org.around.ieuri.tourism.application.catalog.provided.exception

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/**
 * 검색어가 제출됐으나 트림 후 비었다(SPEC-0033 R6).
 * - 인자 생략·null은 "필터 없음"이라 오류가 아니다.
 * - `LikedSpotConnection`에는 payload 래퍼가 없어 최상위 errors[]로 나간다(`searchSpots`는 userErrors[]).
 */
class EmptyQueryException(message: String = "검색어를 입력해 주세요.") : TourismQueryException("EMPTY_QUERY", Classification.BAD_REQUEST, message)
