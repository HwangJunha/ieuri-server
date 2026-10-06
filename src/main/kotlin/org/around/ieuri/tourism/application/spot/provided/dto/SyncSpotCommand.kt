package org.around.ieuri.tourism.application.spot.provided.dto

import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.SpotCategory

/**
 * 관광지 1건의 적재 입력.
 * - [contentTypeId]는 TourAPI 원본 코드로 적재 전용이다. 응답·로그에 노출하지 않는다.
 */
data class SyncSpotCommand(
    val contentId: String,
    val language: DomainLocale,
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val coordinates: Coordinates,
    val category: SpotCategory,
    val contentTypeId: Int,
    val copyright: CopyrightType,
) {
    init {
        require(contentId.isNotBlank()) { "contentId는 필수입니다." }
        require(title.isNotBlank()) { "title은 필수입니다." }
        // 대표이미지 없는 항목은 적재하지 않는다.
        require(thumbnailImageUrl.startsWith("https://")) { "thumbnailImageUrl은 https여야 합니다." }
    }
}
