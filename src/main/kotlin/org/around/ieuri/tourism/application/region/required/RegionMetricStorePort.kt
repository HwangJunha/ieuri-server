package org.around.ieuri.tourism.application.region.required

import org.around.ieuri.tourism.application.region.provided.dto.SyncConcentrationRateCommand
import org.around.ieuri.tourism.application.region.provided.dto.SyncDemandIntensityCommand
import org.around.ieuri.tourism.application.region.provided.dto.SyncVisitorCountCommand

/**
 * 한국관광공사 관광통계 4종 적재 아웃바운드 포트(SPEC-0015).
 * - 자연키 충돌 시 값을 덮어쓴다. 재실행 멱등성은 여기서만 성립한다.
 */
interface RegionMetricStorePort {

    fun upsertVisitorCounts(records: List<SyncVisitorCountCommand>): Int

    fun upsertConcentrationRates(records: List<SyncConcentrationRateCommand>): Int

    fun upsertStayIntensities(records: List<SyncDemandIntensityCommand>): Int

    fun upsertSpendingIntensities(records: List<SyncDemandIntensityCommand>): Int
}
