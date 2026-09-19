package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import java.time.Instant

/**
 * 상세 사전 수집의 대상 조회 포트(SPEC-0037).
 *
 * **대상 판정은 "그 갈래 표에 행이 있는가"다.** 행이 없으면 아직 확보하지 못한 것이고(미확보든
 * 확보실패든 배치 동작은 같다), 행이 있어도 [staleBefore]보다 오래됐으면 재확보 대상이다(FR-007).
 */
interface SpotDetailPrefetchTargetQueryPort {
    /**
     * [part] 갈래를 확보해야 할 장소를 `spot.id` 오름차순으로 [limit]건. [afterSpotId]보다 큰 id만(키셋).
     *
     * 갈래마다 따로 묻는 이유는 네 갈래의 진행이 서로 다르기 때문이다 — 한 쿼리로 묶으면 한 갈래가
     * 막힐 때 나머지가 함께 선다(FR-003).
     */
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
 * 확보할 장소 1건. `spot` 행이 이미 언어별이므로 [language]가 곧 어느 언어 서비스를 부를지를 정하고,
 * 그 언어에 없는 장소는 행이 없어 대상에서 자연히 빠진다(AC-004).
 *
 * [title]·[thumbnailImageUrl]·[copyright]·[address]는 **본문 부재를 적재할 때 NOT NULL 컬럼을
 * 채우는 값**이다(SPEC-0008 R-2). 어차피 같은 `spot` 행을 읽으므로 함께 담아 두면 부재를 만났을 때
 * 다시 조회하지 않아도 된다.
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
