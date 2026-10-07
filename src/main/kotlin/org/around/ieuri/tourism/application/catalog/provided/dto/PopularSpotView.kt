package org.around.ieuri.tourism.application.catalog.provided.dto

/** 인기 spot 1건. [likeCount]는 엣지의 값이다 — 리졸버가 엣지로 옮긴다. */
data class PopularSpotView(
    val spot: SpotView,
    val likeCount: Long,
)
