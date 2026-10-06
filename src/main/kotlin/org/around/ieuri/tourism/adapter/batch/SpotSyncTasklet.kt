package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.spot.required.SpotRepository
import org.around.ieuri.tourism.application.spot.required.SpotSyncPort
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.time.Instant
import java.time.LocalDate

/**
 * 한 언어의 관광지 목록을 전량 수집·적재하고 사라진 항목을 정리하는 Step 본체.
 * - 페이지마다 CONTINUABLE로 커밋하므로 실패 후 재실행이 그 페이지에서 이어진다.
 *
 * @param endedFestivalIds 적재에서 뺄 종료 축제 contentId 조회. 실행 시점에 조회하도록 함수로 받는다.
 */
class SpotSyncTasklet(
    private val language: DomainLocale,
    private val syncPort: SpotSyncPort,
    private val spotRepository: SpotRepository,
    // 생 SQL(upsert) 경로의 포트.
    private val pageSize: Int,
    private val endedFestivalIds: (LocalDate) -> Set<String> = { emptySet() },
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 이번 실행에서 수집한 contentId 전체(정리의 생존 집합). 재시작하면 비어 있고 정리를 건너뛴다. */
    private val collectedIds = HashSet<String>()

    /** 종료 축제 제외 집합의 스냅샷. Step이 시작할 때 한 번만 잡는다. */
    private var endedFestivalSnapshot: Set<String>? = null

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val executionContext = chunkContext.stepContext.stepExecution.executionContext
        val pageNo = executionContext.getInt(KEY_NEXT_PAGE, 1)
        // 시작 페이지를 한 번만 기록한다. 1보다 크면 재개 실행이라 정리를 건너뛴다.
        if (!executionContext.containsKey(KEY_RESUMED_FROM)) {
            executionContext.putInt(KEY_RESUMED_FROM, pageNo)
        }
        // 실행 시각은 첫 페이지에서 한 번 고정한다.
        val syncedAt = executionContext.getString(KEY_SYNCED_AT, Instant.now().toString()).let(Instant::parse)

        val page = syncPort.fetchPage(language, pageNo, pageSize)

        // 종료된 축제는 여기서 뺀다 — 어댑터에서 빼면 collectedIds에도 빠져 삭제된다.
        val excluded = endedFestivalSnapshot
            ?: endedFestivalIds(chunkContext.runDate()).also { endedFestivalSnapshot = it }
        val toStore = if (excluded.isEmpty()) page.records else page.records.filterNot { it.contentId in excluded }
        val written = spotRepository.upsertSpots(toStore, syncedAt)

        // toStore가 아니라 page.records다 — 종료 축제도 생존 집합에 넣어야 삭제되지 않는다.
        // 비관광 제외분(SPEC-0018)은 어댑터가 이미 빼서 여기 없고, 그래서 정리 단계에서 지워진다.
        page.records.forEach { collectedIds += it.contentId }

        val totalWritten = executionContext.getInt(KEY_WRITTEN, 0) + written
        val totalDropped = executionContext.getInt(KEY_DROPPED, 0) + page.droppedCount
        val totalFallback = executionContext.getInt(KEY_CATEGORY_FALLBACK, 0) + page.categoryFallbackCount
        val totalExcluded = executionContext.getInt(KEY_EXCLUDED, 0) + page.excludedCount

        executionContext.putString(KEY_SYNCED_AT, syncedAt.toString())
        executionContext.putInt(KEY_WRITTEN, totalWritten)
        executionContext.putInt(KEY_DROPPED, totalDropped)
        executionContext.putInt(KEY_CATEGORY_FALLBACK, totalFallback)
        executionContext.putInt(KEY_EXCLUDED, totalExcluded)
        contribution.incrementWriteCount(written.toLong())

        val hasMore = page.hasMore(pageNo, pageSize)
        executionContext.putInt(KEY_NEXT_PAGE, if (hasMore) pageNo + 1 else pageNo)

        if (!hasMore) {
            // 폴백 건수가 크면 category_external_mapping 행 누락, 제외 건수가 크면 키워드 설정 오류의 신호다(SPEC-0018).
            log.info(
                "spot sync completed: language={} written={} dropped={} categoryFallback={} excluded={}",
                language,
                totalWritten,
                totalDropped,
                totalFallback,
                totalExcluded,
            )
            executionContext.putInt(KEY_COLLECTED, collectedIds.size)
            cleanup(executionContext, pageNo)
        }

        return if (hasMore) RepeatStatus.CONTINUABLE else RepeatStatus.FINISHED
    }

    /**
     * 사라진 항목 정리 — 수집 집합의 여집합을 지운다. 두 게이트 중 하나라도 빼면 대량 오삭제가 된다:
     *  1. 재개 실행이 아니다 — 재시작하면 집합이 불완전하다.
     *  2. 0건이 아니다 — 업스트림 일시 오류로 빈 목록을 받으면 전량 삭제된다.
     * 게이트에 걸리면 삭제를 건너뛰고 사유를 실행 이력에 남긴다.
     */
    private fun cleanup(executionContext: ExecutionContext, lastPageNo: Int) {
        val collected = collectedIds.size

        val skipReason = when {
            // 1페이지부터 시작하지 않았다면 재개 실행이다.
            executionContext.getInt(KEY_RESUMED_FROM, 1) > 1 ->
                "resumed run (incomplete id set)"

            collected == 0 -> "collected 0 items"

            else -> null
        }

        if (skipReason != null) {
            log.warn("spot cleanup skipped: language={} reason={}", language, skipReason)
            executionContext.putString(KEY_CLEANUP_SKIPPED, skipReason)
            return
        }

        val deleted = spotRepository.deleteMissingSpots(language, collectedIds)
        log.info("spot cleanup: language={} deleted={} surviving={}", language, deleted, collected)
        executionContext.putInt(KEY_DELETED, deleted)
    }

    companion object {
        const val KEY_NEXT_PAGE = "nextPage"
        const val KEY_RESUMED_FROM = "resumedFrom"
        const val KEY_CLEANUP_SKIPPED = "cleanupSkipped"
        const val KEY_DELETED = "deleted"
        const val KEY_SYNCED_AT = "syncedAt"
        const val KEY_WRITTEN = "written"
        const val KEY_DROPPED = "dropped"
        const val KEY_CATEGORY_FALLBACK = "categoryFallback"
        const val KEY_EXCLUDED = "excluded"
        const val KEY_COLLECTED = "collected"
    }
}
