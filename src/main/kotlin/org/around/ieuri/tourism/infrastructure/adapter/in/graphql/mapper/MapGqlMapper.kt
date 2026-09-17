package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.around.ieuri.common.graphql.generated.types.MapBoundsInput as GqlMapBoundsInput
import org.around.ieuri.common.graphql.generated.types.MapSpot as GqlMapSpot
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory

/**
 * 입력 bbox → 도메인 VO. 좌표 범위·뒤집힘이 유효하지 않으면 **null**이다 — 도메인이 GraphQL
 * 오류 코드를 모르므로 어느 코드로 거절할지는 호출하는 리졸버가 정한다(둘 다 INVALID_BBOX).
 */
fun GqlMapBoundsInput.toDomain(): MapBounds? =
    MapBounds.ofOrNull(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng)

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
