package org.around.ieuri.tourism.application.spot.required

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.Spot
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotFestival
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import java.time.Instant
import java.time.LocalDate

/**
 * 원본 관광지 저장소. 원본(`spot`)과 그에 딸린 축제·상세를 쓴다. 수집 배치와 상세 적재가 쓴다.
 * - 원본 삭제는 축제·상세를 FK CASCADE로 함께 지운다.
 */
interface SpotRepository {

    /**
     * 한 페이지를 자연키 (contentId, language)로 upsert하고 실제로 쓴 행 수를 돌려준다.
     * - `last_synced_at`은 항상 [syncedAt]으로, `updated_at`은 값이 바뀐 행만 올린다.
     */
    fun upsertSpots(spots: List<Spot>, syncedAt: Instant): Int

    /**
     * [language]에서 [survivingContentIds]에 없는 원본을 지우고 지운 수를 돌려준다.
     * - 빈 집합이면 `IllegalArgumentException`이다 — 그 언어가 전량 삭제되는 것을 막는다.
     */
    fun deleteMissingSpots(language: DomainLocale, survivingContentIds: Set<String>): Int

    /** 축제 한 페이지를 upsert하고 실제로 쓴 행 수를 돌려준다. 대응하는 원본이 없는 항목은 건너뛴다. */
    fun upsertFestivals(festivals: List<SpotFestival>, syncedAt: Instant): Int

    /** [language]에서 종료일이 [asOf]보다 이전인 축제 원본을 지운다. 종료일 당일은 지우지 않는다(`FestivalPeriod.isEndedAsOf`). */
    fun deleteEndedFestivals(language: DomainLocale, asOf: LocalDate): Int

    /**
     * [language]의 축제 카테고리 원본 중 [survivingContentIds]에 없는 것을 지운다.
     * - 빈 집합이면 `IllegalArgumentException`이다.
     */
    fun deleteMissingFestivals(language: DomainLocale, survivingContentIds: Set<String>): Int

    /** [language]에서 종료일이 [asOf]보다 이전인 축제의 contentId. 수집이 이것을 되살리지 않게 거른다. */
    fun findEndedFestivalContentIds(language: DomainLocale, asOf: LocalDate): Set<String>

    /** 본문을 쓰고 확보 시각을 새로 시작한다. 부재였던 행은 본문 있음으로 돌아간다. */
    fun saveDetail(spotId: Long, detail: SpotDetail)

    /** 본문 부재를 쓴다(SPEC-0008 R-3). [absent]는 원본 값으로 채운 상세다. */
    fun saveDetailAbsence(spotId: Long, absent: SpotDetail)

    /** 갤러리 전량 교체. */
    fun saveGallery(spotId: Long, gallery: List<SpotImage>)

    /** 운영 정보. 값이 하나도 없어도 행을 남긴다 — 미적재와 구분한다. */
    fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?)

    /** 이용안내 전량 교체. */
    fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>)
}
