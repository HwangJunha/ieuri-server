package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import java.time.Instant

/**
 * 상세 사전 수집의 대상 조회 포트(SPEC-0037).
 * - 그 갈래 표에 행이 없거나, 행이 [staleBefore]보다 오래됐으면 대상이다(FR-007).
 */
interface SpotDetailPrefetchTargetQueryPort {
    /** [part] 갈래를 확보해야 할 장소를 `spot.id` 오름차순으로 [limit]건. [afterSpotId]보다 큰 id만(키셋). */
    fun findTargets(part: SpotDetailPart, afterSpotId: Long, limit: Int, staleBefore: Instant): List<PrefetchTarget>
}

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
 * 확보할 장소 1건. [language]가 어느 언어 서비스를 부를지 정한다(AC-004).
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
)
