package org.around.ieuri.tourism.application.spot.required

import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import org.around.ieuri.tourism.domain.spot.Spot
import org.around.ieuri.tourism.domain.spot.SpotBody
import org.around.ieuri.tourism.domain.spot.SpotFestival
import org.around.ieuri.tourism.domain.spot.SpotGallery
import org.around.ieuri.tourism.domain.spot.SpotInformation
import org.around.ieuri.tourism.domain.spot.SpotIntroduction
import java.time.Instant

/**
 * 원본 관광지 저장소. 원본(`spot`)과 그에 딸린 축제·상세를 쓴다. 수집 배치와 상세 적재가 쓴다.
 * - 원본 삭제는 축제·상세를 FK CASCADE로 함께 지운다.
 */
interface SpotRepository {

    /** 저장된 원본 하나. 없으면 null이다. */
    fun findById(spotId: Long): Spot?

    /** [language]의 저장된 원본 중 [contentIds]에 든 것. 없는 contentId는 결과에 없다. */
    fun findByContentIds(language: DomainLocale, contentIds: Collection<String>): List<Spot>

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

    /** 축제 한 페이지를 원본 id로 upsert하고 쓴 행 수를 돌려준다. `updated_at`은 값이 바뀐 행만 올린다. */
    fun upsertFestivals(festivals: List<SpotFestival>, syncedAt: Instant): Int

    /** [language]의 원본 중 [contentIds]를 지우고 지운 행 수를 돌려준다. 무엇을 지울지는 호출자가 도메인으로 정한다. */
    fun deleteSpots(language: DomainLocale, contentIds: Set<String>): Int

    /**
     * [language]의 축제 카테고리 원본 중 [survivingContentIds]에 없는 것을 지운다.
     * - 빈 집합이면 `IllegalArgumentException`이다.
     */
    fun deleteMissingFestivals(language: DomainLocale, survivingContentIds: Set<String>): Int

    /** [language]의 축제 원본 contentId → 행사 기간. 끝났는지는 도메인([org.around.ieuri.tourism.domain.spot.spec.EndedFestival])이 판정한다. */
    fun findFestivalPeriods(language: DomainLocale): Map<String, FestivalPeriod>

    /** 본문(또는 부재, SPEC-0008 R-3)을 쓰고 확보 시각을 새로 시작한다. 부재였던 행에 본문이 오면 본문 있음으로 돌아간다. */
    fun saveBody(body: SpotBody)

    /** 갤러리 전량 교체. */
    fun saveGallery(gallery: SpotGallery)

    /** 운영 정보. 값이 하나도 없어도 행을 남긴다 — 미적재와 구분한다. */
    fun saveIntro(introduction: SpotIntroduction)

    /** 이용안내 전량 교체. */
    fun saveInfoItems(information: SpotInformation)
}
