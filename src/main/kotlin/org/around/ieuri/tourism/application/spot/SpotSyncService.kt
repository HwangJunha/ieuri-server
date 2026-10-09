package org.around.ieuri.tourism.application.spot

import org.around.ieuri.common.pagination.OffsetPage
import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.spot.provided.SpotSyncUseCase
import org.around.ieuri.tourism.application.spot.provided.dto.FestivalSweepResult
import org.around.ieuri.tourism.application.spot.provided.dto.SpotSyncPageResult
import org.around.ieuri.tourism.application.spot.required.FestivalSyncPort
import org.around.ieuri.tourism.application.spot.required.SpotRepository
import org.around.ieuri.tourism.application.spot.required.SpotSyncPort
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.SpotHarvest
import org.around.ieuri.tourism.domain.spot.spec.EndedFestival
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

/** 원본 관광지·축제 동기화. TourAPI 호출은 첫 쿼리 전에 한다 — 연결을 잡은 채 기다리지 않는다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotSyncService(
    private val spotSyncPort: SpotSyncPort,
    private val festivalSyncPort: FestivalSyncPort,
    private val spotRepository: SpotRepository,
) : SpotSyncUseCase {

    override fun syncSpotPage(language: DomainLocale, pageNo: Int, pageSize: Int, syncedAt: Instant, runDate: LocalDate): SpotSyncPageResult {
        val page = spotSyncPort.fetchPage(language, pageNo, pageSize)

        val ended = EndedFestival(spotRepository.findFestivalPeriods(language), runDate)
        val written = spotRepository.upsertSpots(page.records.filterNot { ended.isSatisfiedBy(it.contentId) }, syncedAt)

        return SpotSyncPageResult(
            written = written,
            contentIds = page.records.map { it.contentId },
            hasMore = OffsetPage.hasNextPage(pageNo, pageSize, page.totalCount),
            dropped = page.droppedCount,
            categoryFallback = page.categoryFallbackCount,
            excluded = page.excludedCount,
        )
    }

    override fun sweepSpots(language: DomainLocale, harvest: SpotHarvest): Int = spotRepository.deleteMissingSpots(language, harvest.survivors())

    override fun syncFestivalPage(language: DomainLocale, pageNo: Int, pageSize: Int, syncedAt: Instant, runDate: LocalDate): SpotSyncPageResult {
        val page = festivalSyncPort.fetchPage(language, runDate, pageNo, pageSize)

        // 축제는 그 원본이 만든다 — 원본이 없는 행은 건너뛴다.
        val origins = spotRepository.findByContentIds(language, page.records.map { it.contentId }).associateBy { it.contentId }
        val festivals = page.records.mapNotNull { record -> origins[record.contentId]?.hold(record.period, record.progressType, record.festivalType) }
        val written = spotRepository.upsertFestivals(festivals, syncedAt)

        return SpotSyncPageResult(
            written = written,
            contentIds = page.records.map { it.contentId },
            hasMore = OffsetPage.hasNextPage(pageNo, pageSize, page.totalCount),
            dropped = page.droppedCount,
        )
    }

    override fun sweepFestivals(language: DomainLocale, harvest: SpotHarvest, runDate: LocalDate): FestivalSweepResult {
        val survivors = harvest.survivors()

        // 끝난 축제를 먼저 지운다 — 그다음 "이번 수집에 없는" 축제를 지운다.
        val periods = spotRepository.findFestivalPeriods(language)
        val endedFestival = EndedFestival(periods, runDate)
        val ended = spotRepository.deleteSpots(language, periods.keys.filter(endedFestival::isSatisfiedBy).toSet())
        val missing = spotRepository.deleteMissingFestivals(language, survivors)
        return FestivalSweepResult(ended = ended, missing = missing)
    }
}
