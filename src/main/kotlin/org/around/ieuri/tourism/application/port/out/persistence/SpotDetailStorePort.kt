package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import java.time.Instant

/**
 * 관광지 상세의 DB 캐시 조회 포트. 오퍼레이션 단위로 나뉜다. 적재는 [SpotDetailUpsertPort]가 맡는다.
 */
interface SpotDetailStorePort {
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
