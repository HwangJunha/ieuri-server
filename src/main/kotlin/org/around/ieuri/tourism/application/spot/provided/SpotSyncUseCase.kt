package org.around.ieuri.tourism.application.spot.provided

import org.around.ieuri.tourism.application.spot.provided.dto.FestivalSweepResult
import org.around.ieuri.tourism.application.spot.provided.dto.SpotSyncPageResult
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.SpotHarvest
import java.time.Instant
import java.time.LocalDate

/**
 * 원본 관광지·축제의 전량 동기화(SPEC-0004·SPEC-0007). 배치가 페이지마다 부르며 호출 하나가 트랜잭션 하나다.
 * - 정리(sweep)는 한 언어의 수집이 끝난 뒤 한 번만 부른다. 수집 집합을 믿을 수 없으면 `SweepBlockedException`이다.
 */
interface SpotSyncUseCase {

    /** [language]의 [pageNo] 페이지(1부터)를 받아 적재한다. 종료된 축제는 적재하지 않는다 — 정리가 지운 것을 되살린다. */
    fun syncSpotPage(language: DomainLocale, pageNo: Int, pageSize: Int, syncedAt: Instant, runDate: LocalDate): SpotSyncPageResult

    /** 이번 수집에 없던 원본을 지우고 지운 수를 돌려준다. */
    fun sweepSpots(language: DomainLocale, harvest: SpotHarvest): Int

    /** 축제 한 페이지를 받아 적재한다. [runDate] 기준 유효한 행사만 온다(`searchFestival2`). */
    fun syncFestivalPage(language: DomainLocale, pageNo: Int, pageSize: Int, syncedAt: Instant, runDate: LocalDate): SpotSyncPageResult

    /** 끝난 축제와 이번 수집에 없던 축제를 지운다. 끝난 축제를 먼저 지운다 — 두 대상이 겹쳐 순서가 사유별 건수를 가른다. */
    fun sweepFestivals(language: DomainLocale, harvest: SpotHarvest, runDate: LocalDate): FestivalSweepResult
}
