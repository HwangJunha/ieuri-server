package org.around.ieuri.tourism.application.spot.required

import org.around.ieuri.tourism.application.spot.provided.dto.SyncFestivalCommand
import org.around.ieuri.tourism.application.spot.provided.dto.SyncSpotCommand
import org.around.ieuri.tourism.domain.shared.DomainLocale
import java.time.Instant
import java.time.LocalDate

/**
 * TourAPI 원본(언어별 `spot`과 축제 `spot_festival`) 저장소. 수집 배치가 쓴다.
 * - 삭제는 전부 `spot` 부모 행을 지우며, `spot_festival`은 FK CASCADE로 따라간다.
 */
interface SpotRepository {

    /**
     * 한 페이지 분량을 upsert한다. 자연키 (contentId, language) 충돌 시 전체 컬럼을 덮어쓰고,
     * [syncedAt]을 건드린 행의 `last_synced_at`에 기록한다. 실제로 upsert된 행 수를 돌려준다.
     */
    fun upsertSpots(spots: List<SyncSpotCommand>, syncedAt: Instant): Int

    /**
     * [language]에서 [survivingContentIds]에 없는 행을 삭제하고 삭제 행 수를 돌려준다.
     * - 빈 집합이면 `IllegalArgumentException`이다 — 그 언어가 전량 삭제되는 것을 막는다. 호출자의 안전 판정과 별개다.
     */
    fun deleteMissingSpots(language: DomainLocale, survivingContentIds: Set<String>): Int

    /**
     * 축제 한 페이지를 upsert하고 실제 적재된 행 수를 돌려준다. 자연키 (contentId, language) 충돌 시 덮어쓴다.
     * - 대응하는 `spot` 행이 없는 항목은 조용히 건너뛴다.
     */
    fun upsertFestivals(festivals: List<SyncFestivalCommand>, syncedAt: Instant): Int

    /**
     * [language]에서 종료일이 [asOf]보다 이전인 축제를 삭제하고 삭제 행 수를 돌려준다. 종료일 당일은 지우지 않는다.
     * - 이 경계는 [org.around.ieuri.tourism.domain.spot.FestivalPeriod.isEndedAsOf]와 같아야 한다.
     */
    fun deleteEndedFestivals(language: DomainLocale, asOf: LocalDate): Int

    /**
     * [language]의 축제 중 [survivingContentIds]에 없는 것을 삭제하고 삭제 행 수를 돌려준다.
     * - 빈 집합이면 `IllegalArgumentException`이다 — 그 언어의 축제가 전량 삭제되는 것을 막는다.
     */
    fun deleteMissingFestivals(language: DomainLocale, survivingContentIds: Set<String>): Int

    /**
     * [language]에서 종료일이 [asOf]보다 이전인 축제의 contentId 집합.
     * - 관광지 수집 Step이 이것을 제외하지 않으면 [deleteEndedFestivals]가 지운 행사를 되살린다.
     */
    fun findEndedFestivalContentIds(language: DomainLocale, asOf: LocalDate): Set<String>
}
