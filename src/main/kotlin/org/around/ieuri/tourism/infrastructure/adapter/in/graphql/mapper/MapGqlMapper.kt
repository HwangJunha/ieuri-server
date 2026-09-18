package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.common.graphql.generated.types.MapSpot as GqlMapSpot
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory

/**
 * 지도 마커 응답 변환(POL-033). `mapViewport`와 `mapRecommendedSpots`가 같은 `MapSpot`을
 * 내보내므로 변환을 한 곳에 둔다 — 리졸버 안 private 함수로 두면 둘이 각자 갖게 되고,
 * 단독으로 호출할 수 없어 테스트도 리졸버를 세워야 한다.
 *
 * **라벨·아이콘을 채우지 않는다.** 마커는 코드만 쓰고 표시 문구는 `availableSpotCategories`가
 * 담당한다 — 기존 `mapViewport` 동작을 그대로 옮긴 것이다.
 */
fun MapSpotView.toGql(): GqlMapSpot = GqlMapSpot(
    id = id,
    title = title,
    thumbnailImageUrl = thumbnailImageUrl,
    category = GqlSpotCategory(code = category.code, label = category.code, iconUrl = null),
    lng = lng,
    lat = lat,
)
