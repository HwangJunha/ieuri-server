package org.around.ieuri.tourism.domain.spot

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale

/**
 * 언어별 TourAPI 원본 관광지 하나. 자연키는 (contentId, language)이며 배치가 전량 수집해 맞춘다.
 * - 상세 본문·갤러리·운영 정보·이용안내([SpotDetail] 등)와 축제 기간([SpotFestival])이 이 원본에 딸린다.
 * - [contentTypeId]는 TourAPI 원본 코드로 적재 전용이다. 응답·로그에 노출하지 않는다.
 */
data class Spot(
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
