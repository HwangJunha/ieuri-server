package org.around.ieuri.tourism.application.spot.required

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro

/**
 * 관광지 캐시 아웃바운드 포트(SPEC-0003). 오퍼레이션 단위로 키를 나누며 TourAPI 파생 필드만 담는다.
 * - 구현체는 저장소 장애를 삼킨다(get→null, put→무시).
 * - 키는 (오퍼레이션[, category], spotId)다. spotId가 이미 언어별이라 언어는 넣지 않는다.
 */
interface SpotCachePort {
    /** 상세 본문. 미스·장애면 null. 본문 부재도 히트이며 [CachedDetail.bodyAbsent]로 표시된다. */
    fun getDetail(spotId: Long): CachedDetail?

    /** 본문 존재. */
    fun putDetail(spotId: Long, detail: SpotDetail)

    /** 본문 부재. [fallback]은 `spot` 행에서 구성한 응답용 값이다. */
    fun putDetailAbsence(spotId: Long, fallback: SpotDetail)

    /** 갤러리. 미스·장애면 null(빈 목록과 구분된다). */
    fun getGallery(spotId: Long): List<SpotImage>?
    fun putGallery(spotId: Long, gallery: List<SpotImage>)

    /** 운영 정보. 미스·장애면 래퍼 자체가 null, 히트했지만 값이 없으면 [CachedIntro.value]가 null. */
    fun getIntro(spotId: Long, category: SpotCategory): CachedIntro?
    fun putIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?)

    /** 이용안내 항목. 미스·장애면 null. */
    fun getInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>?
    fun putInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>)
}

/** 캐시 히트 여부와 "값이 null임"을 구분하기 위한 래퍼(intro 전용). */
data class CachedIntro(val value: SpotIntro?)

/** 캐시된 상세 본문. [bodyAbsent]가 true면 업스트림 본문 부재이며 [detail]은 `spot` 행에서 구성된 값이다. */
data class CachedDetail(
    val detail: SpotDetail,
    val bodyAbsent: Boolean,
)
