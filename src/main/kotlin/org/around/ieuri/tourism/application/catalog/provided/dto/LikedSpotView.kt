package org.around.ieuri.tourism.application.catalog.provided.dto

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import java.time.Instant

/**
 * 좋아요 목록 항목 1건. 상세가 부재한 spot도 목록에 나타난다.
 * - [likedAt]은 노드가 아니라 엣지의 값이다 — 리졸버가 엣지로 옮긴다.
 */
data class LikedSpotView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    val address: String?,
    val category: SpotCategory,
    val copyright: CopyrightType,
    val likedAt: Instant,
    /** 소속 카탈로그. 커서의 정렬 키이며 응답에는 나가지 않는다. */
    val catalogId: Long,
) {
    val attributionText: String get() = copyright.attributionText
}
