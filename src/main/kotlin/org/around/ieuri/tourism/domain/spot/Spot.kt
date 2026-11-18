package org.around.ieuri.tourism.domain.spot

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod

/**
 * 언어별 TourAPI 원본 관광지 하나. 자연키는 (contentId, language)이며 배치가 전량 수집해 맞춘다.
 * - 상세 본문·갤러리·운영 정보·이용안내와 축제 기간이 이 원본에 딸린다. 자식은 이 루트의 메서드로만 만들고, 만든 자식이 원본 [id]를 가진다.
 * - 저장된 원본([id]가 있음)에만 자식이 붙는다 — 아니면 [SpotNotSavedException].
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
    /** 저장된 원본의 id. 수집해 아직 적재하지 않은 원본은 null이다. */
    val id: Long? = null,
) {
    init {
        require(contentId.isNotBlank()) { "contentId는 필수입니다." }
        require(title.isNotBlank()) { "title은 필수입니다." }
        // 대표이미지 없는 항목은 적재하지 않는다.
        require(thumbnailImageUrl.startsWith("https://")) { "thumbnailImageUrl은 https여야 합니다." }
    }

    /** 확보한 상세 본문. */
    fun describe(detail: SpotDetail) = SpotBody(savedId(), detail, absent = false)

    /** 상류에 본문이 없음을 확인했다. [fallback]은 NOT NULL 컬럼을 채울 값이다(SPEC-0008 R-3). */
    fun describeAbsence(fallback: SpotDetail) = SpotBody(savedId(), fallback, absent = true)

    /** 갤러리 전량. 순서가 곧 표시 순서다. */
    fun showcase(images: List<SpotImage>) = SpotGallery(savedId(), images)

    /** [category]로 읽은 운영 정보. 값이 없으면(null) "물었고 없었다"로 남는다. */
    fun introduce(category: SpotCategory, intro: SpotIntro?) = SpotIntroduction(savedId(), category, intro)

    /** [category]로 읽은 이용안내 전량. */
    fun inform(category: SpotCategory, items: List<SpotInfoItem>) = SpotInformation(savedId(), category, items)

    /** 축제 원본의 기간과 분류(SPEC-0007). */
    fun hold(period: FestivalPeriod, progressType: String?, festivalType: String?) = SpotFestival(savedId(), period, progressType, festivalType)

    private fun savedId(): Long = id ?: throw SpotNotSavedException(contentId, language)
}
