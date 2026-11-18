package org.around.ieuri.community.domain.lookup

import org.around.ieuri.community.domain.post.TargetCategory

/** community가 보는 장소(tourism 소유)의 요약. [id]는 `spot.id`가 아니라 카탈로그 id다(SPEC-0019). */
data class SpotSummary(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    /** spot이 속한 카테고리. 글 작성 시 기본값 제안에 쓴다. */
    val category: TargetCategory?,
    /** 경도·위도. 글을 만들 때 글에 복사해 둔다. 조회 응답으로는 내려주지 않는다. */
    val longitude: Double,
    val latitude: Double,
)
