package org.around.ieuri.tourism.application.detail.required

import org.around.ieuri.tourism.domain.detail.SpotDetail
import org.around.ieuri.tourism.domain.detail.SpotImage
import org.around.ieuri.tourism.domain.detail.SpotInfoItem
import org.around.ieuri.tourism.domain.detail.SpotIntro
import org.around.ieuri.tourism.domain.shared.SpotCategory
import java.time.Instant

/**
 * 관광지 상세의 DB 캐시 저장소. 모든 키가 `spot_id`다.
 * - 적재는 전부 `INSERT ... ON CONFLICT`이고, 조회는 미적재(null)와 "조회했으나 없음"을 구분한다.
 */
interface SpotDetailRepository {
    /**
     * 상세 본문. 미적재면 null.
     * - "본문 없음"은 값의 null 여부가 아니라 [StoredDetail.bodyAbsent]로 판별한다.
     */
    fun findDetail(spotId: Long): StoredDetail?

    /** 갤러리. 미적재면 null. 빈 갤러리는 미적재와 구분하지 않는다. */
    fun findGallery(spotId: Long): List<SpotImage>?

    /** 운영 정보. 미적재면 null, 적재됐으나 값이 없으면 `StoredIntro(null)`. */
    fun findIntro(spotId: Long, category: SpotCategory): StoredIntro?

    /** 이용안내. 미적재면 null. 빈 목록은 미적재와 구분하지 않는다. */
    fun findInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>?

    /** 본문이 존재하는 경우의 적재. */
    fun saveDetail(spotId: Long, detail: SpotDetail)

    /**
     * 본문 부재의 적재. NOT NULL 컬럼을 채울 [fallback] 값(`spot` 행에서 온다)을 함께 받는다.
     * - 이미 부재 행이 있으면 `fetched_at`을 갱신한다.
     */
    fun saveDetailAbsence(spotId: Long, fallback: SpotDetail)

    /** 갤러리 전량 교체. */
    fun saveGallery(spotId: Long, gallery: List<SpotImage>)

    /** 운영 정보 적재. 값이 하나도 없어도 행을 남긴다. */
    fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?)

    /** 이용안내 전량 교체. */
    fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>)
}

/** 적재 여부와 "값이 null임"을 구분하기 위한 래퍼(intro 전용). */
data class StoredIntro(val value: SpotIntro?)

/**
 * 적재된 상세 본문 1건.
 * - [bodyAbsent]가 true면 [detail]은 `spot` 행에서 빌려온 값이다(overview·homepage·tel은 null).
 * - 부재 행은 [fetchedAt]이 신선도 임계값을 넘기면 재조회된다.
 */
data class StoredDetail(
    val detail: SpotDetail,
    val bodyAbsent: Boolean,
    val fetchedAt: Instant,
)
