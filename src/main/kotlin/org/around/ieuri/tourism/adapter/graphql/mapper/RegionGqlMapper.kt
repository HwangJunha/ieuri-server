package org.around.ieuri.tourism.adapter.graphql.mapper

import org.around.ieuri.tourism.application.catalog.provided.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.region.provided.dto.RecommendedRegionItemView
import org.around.ieuri.tourism.application.region.provided.dto.RecommendedRegionView
import org.around.ieuri.tourism.application.region.provided.dto.RegionScoreView
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.common.graphql.generated.types.CoordinatesInput as GqlCoordinatesInput
import org.around.ieuri.common.graphql.generated.types.RecommendedRegion as GqlRecommendedRegion
import org.around.ieuri.common.graphql.generated.types.RecommendedRegionResult as GqlRecommendedRegionResult
import org.around.ieuri.common.graphql.generated.types.RegionScore as GqlRegionScore
import org.around.ieuri.common.graphql.generated.types.RegionScoreFactor as GqlRegionScoreFactor

// 지역 추천 응답·입력 변환(POL-033).

/** 좌표 범위 위반은 `INVALID_COORDINATES`다(R29). 예외 메시지에 좌표를 담지 않는다(POL-013). */
fun GqlCoordinatesInput.toDomain(): Coordinates = runCatching { Coordinates(latitude = lat, longitude = lng) }
    .getOrElse { throw InvalidCoordinatesException("좌표가 올바르지 않습니다.") }

fun RecommendedRegionView.toGql() = GqlRecommendedRegionResult(
    regions = regions.map { it.toGql() },
)

fun RecommendedRegionItemView.toGql() = GqlRecommendedRegion(
    code = code,
    name = name,
    provinceName = provinceName,
    scores = scores.map { it.toGql() },
    scoreFactorCount = scoreFactorCount,
    center = center.toGql(),
)

fun RegionScoreView.toGql() = GqlRegionScore(
    factor = GqlRegionScoreFactor.valueOf(score.factor.name),
    value = score.value,
    rank = rank,
)
