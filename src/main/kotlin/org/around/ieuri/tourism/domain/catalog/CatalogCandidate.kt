package org.around.ieuri.tourism.domain.catalog

/** 같은 제목 키를 가진 KO 카탈로그 후보. 좌표·썸네일로 동명이인과 같은 건물의 다른 매장을 가른다. */
data class CatalogCandidate(
    val catalogId: Long,
    val mapX: Double,
    val mapY: Double,
    val thumbnailImageUrl: String,
)
