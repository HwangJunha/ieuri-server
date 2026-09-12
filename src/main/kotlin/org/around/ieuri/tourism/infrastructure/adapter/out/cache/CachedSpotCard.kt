package org.around.ieuri.tourism.infrastructure.adapter.out.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 추천 목록 묶음 캐시의 카드 1장(어댑터 내부 직렬화 DTO). read DTO를 Jackson으로 직접 굽지 않는
 * 이유는 [CachedSpotDetail]과 같다 — 값 클래스([SpotCategory])는 Jackson 왕복이 보장되지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedSpotCard(
    val id: Long = 0,
    val title: String = "",
    val address: String? = null,
    val thumbnailImageUrl: String = "",
    val mapX: Double = 0.0,
    val mapY: Double = 0.0,
    /** 카테고리 **코드 문자열**로 담는다. */
    val category: String = SpotCategory.TOURIST_SPOT.code,
    val copyright: CopyrightType = CopyrightType.UNSPECIFIED,
    val distanceMeters: Double = 0.0,
) {
    fun toSearchResult(): SpotSearchResult = SpotSearchResult(
        spotId = id,
        title = title,
        address = address,
        thumbnailImageUrl = thumbnailImageUrl,
        mapX = mapX,
        mapY = mapY,
        category = SpotCategory(category),
        copyright = copyright,
        distanceMeters = distanceMeters,
    )

    companion object {
        fun from(card: SpotSearchResult): CachedSpotCard = CachedSpotCard(
            id = card.spotId,
            title = card.title,
            address = card.address,
            thumbnailImageUrl = card.thumbnailImageUrl,
            mapX = card.mapX,
            mapY = card.mapY,
            category = card.category.code,
            copyright = card.copyright,
            distanceMeters = card.distanceMeters,
        )
    }
}
