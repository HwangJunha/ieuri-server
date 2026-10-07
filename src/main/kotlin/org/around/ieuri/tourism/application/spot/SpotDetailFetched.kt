package org.around.ieuri.tourism.application.spot

import org.around.ieuri.tourism.domain.shared.SpotCategory
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro

/**
 * upstream에서 상세를 가져왔을 때 발행되는 이벤트(SPEC-0004 R-14). 리스너가 DB에 적재한다.
 * - 조회 응답은 적재를 기다리지 않는다(AC-39).
 */
data class SpotDetailFetched(
    val spotId: Long,
    val detail: SpotDetail,
)

/**
 * upstream에 상세 본문이 없음을 확인했을 때 발행되는 이벤트(SPEC-0008 R-3).
 * - NOT NULL 컬럼은 [fallback](= `spot` 행의 값)으로 채운다.
 * - upstream 실패 시에는 발행하지 않는다(AC-16·AC-4).
 */
data class SpotDetailAbsent(
    val spotId: Long,
    val fallback: SpotDetail,
)

data class SpotGalleryFetched(
    val spotId: Long,
    val gallery: List<SpotImage>,
)

/** [intro]는 정상적으로 null일 수 있다 — "값 없음"도 적재한다. */
data class SpotIntroFetched(
    val spotId: Long,
    val category: SpotCategory,
    val intro: SpotIntro?,
)

data class SpotInfoItemsFetched(
    val spotId: Long,
    val category: SpotCategory,
    val items: List<SpotInfoItem>,
)
