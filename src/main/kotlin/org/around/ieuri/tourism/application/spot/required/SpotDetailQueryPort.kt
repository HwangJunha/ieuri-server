package org.around.ieuri.tourism.application.spot.required

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import java.time.Instant

/**
 * 적재된 상세 읽기(SPEC-0004 R-14·SPEC-0037). 모든 키가 원본 `spot.id`다.
 * - 미적재(null)와 "조회했으나 없음"을 구분한다.
 */
interface SpotDetailQueryPort {
    /** 상세 본문. 미적재면 null. "본문 없음"은 값의 null 여부가 아니라 [StoredDetail.bodyAbsent]로 판별한다. */
    fun findDetail(spotId: Long): StoredDetail?

    /** 갤러리. 미적재면 null. 빈 갤러리는 미적재와 구분하지 않는다. */
    fun findGallery(spotId: Long): List<SpotImage>?

    /** 운영 정보. 미적재면 null, 적재됐으나 값이 없으면 `StoredIntro(null)`. */
    fun findIntro(spotId: Long, category: SpotCategory): StoredIntro?

    /** 이용안내. 미적재면 null. 빈 목록은 미적재와 구분하지 않는다. */
    fun findInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>?

    /**
     * [part] 갈래를 확보해야 할 원본을 `spot.id` 오름차순으로 [limit]건. [afterSpotId]보다 큰 id만(키셋).
     * - 그 갈래 표에 행이 없거나, 행이 [staleBefore]보다 오래됐으면 대상이다(FR-007).
     */
    fun findPrefetchTargets(part: SpotDetailPart, afterSpotId: Long, limit: Int, staleBefore: Instant): List<PrefetchTarget>
}

/** 적재 여부와 "값이 null임"을 구분하기 위한 래퍼(intro 전용). */
data class StoredIntro(val value: SpotIntro?)

/**
 * 적재된 상세 본문 1건.
 * - [bodyAbsent]가 true면 [detail]은 원본 목록에서 빌려온 값이다(overview·homepage·tel은 null).
 */
data class StoredDetail(
    val detail: SpotDetail,
    val bodyAbsent: Boolean,
    val fetchedAt: Instant,
)

/** 상세의 네 갈래. TourAPI 오퍼레이션 하나와 우리 표 하나에 각각 대응한다. */
enum class SpotDetailPart {
    /** detailCommon2 → `spot_detail` */
    BODY,

    /** detailImage2 → `spot_detail_image` */
    GALLERY,

    /** detailIntro2 → `spot_detail_intro` */
    INTRO,

    /** detailInfo2 → `spot_detail_info` */
    INFO,
}

/**
 * 확보할 원본 1건. [language]가 어느 언어 서비스를 부를지 정한다(AC-004).
 * - [title]·[thumbnailImageUrl]·[copyright]·[address]는 본문 부재 행의 NOT NULL 컬럼을 채운다(SPEC-0008 R-2).
 */
data class PrefetchTarget(
    val spotId: Long,
    val contentId: String,
    val language: DomainLocale,
    val category: SpotCategory,
    val title: String,
    val thumbnailImageUrl: String,
    val copyright: CopyrightType,
    val address: String?,
) {
    /** 상류에 본문이 없을 때 남길 상세. */
    fun absentDetail(): SpotDetail = SpotDetail.absent(title, category, thumbnailImageUrl, address, copyright)
}
