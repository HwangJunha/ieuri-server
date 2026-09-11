package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.query.SpotView

/**
 * 반경 조회 read DTO → 응답 뷰. attributionText는 copyright에서 파생되므로 여기서 도출한다 —
 * 적재하지 않는 이유이기도 하다(컬럼으로 두면 둘이 어긋날 수 있다). 거리는 DB가 계산한 값 그대로다.
 */
internal fun SpotSearchResult.toView(): SpotView = SpotView(
    id = spotId,
    title = title,
    address = address,
    thumbnailImageUrl = thumbnailImageUrl,
    distanceMeters = distanceMeters,
    category = category,
    copyright = copyright,
    attributionText = copyright.attributionText,
    mapX = mapX,
    mapY = mapY,
)
