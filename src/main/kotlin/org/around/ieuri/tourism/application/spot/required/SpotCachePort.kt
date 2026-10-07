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
    /** 상세 본문. 미스·장애면 null. 본문 부재로 확인돼 `spot` 행에서 구성한 값도 히트다. */
    fun getDetail(spotId: Long): SpotDetail?

    /** 상세 본문. 본문 부재면 [detail]은 `spot` 행에서 구성한 응답용 값이다. */
    fun putDetail(spotId: Long, detail: SpotDetail)

    /** 갤러리. 미스·장애면 null(빈 목록과 구분된다). */
    fun getGallery(spotId: Long): List<SpotImage>?
    fun putGallery(spotId: Long, gallery: List<SpotImage>)

    /** 운영 정보. 미스·장애면 래퍼 자체가 null, 히트했지만 값이 없으면 [KnownIntro.value]가 null. */
    fun getIntro(spotId: Long, category: SpotCategory): KnownIntro?
    fun putIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?)

    /** 이용안내 항목. 미스·장애면 null. */
    fun getInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>?
    fun putInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>)
}
