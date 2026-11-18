package org.around.ieuri.tourism.application.spot.provided.dto

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType

/** spotDetail 본문(SPEC-0003). 갤러리·운영정보·이용안내·좋아요 수는 필드별로 따로 조회된다. */
data class SpotDetailView(
    val id: Long,
    val title: String,
    val category: SpotCategory,
    val thumbnailImageUrl: String,
    val overview: String?,
    val address: String?,
    val homepage: String?,
    val tel: String?,
    val copyright: CopyrightType,
    /** 경도. 본문 캐시에 담기지 않고 뷰 조립 시점에 카탈로그에서 얹힌다. */
    val mapX: Double,
    /** 위도. 출처는 [mapX]와 같다. */
    val mapY: Double,
) {
    val attributionText: String get() = copyright.attributionText
}
