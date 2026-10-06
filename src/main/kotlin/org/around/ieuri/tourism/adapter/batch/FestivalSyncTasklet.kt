package org.around.ieuri.tourism.adapter.batch

import org.around.ieuri.tourism.application.spot.required.FestivalSyncPort
import org.around.ieuri.tourism.application.spot.required.SpotRepository
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
 * 한 언어의 축제 정보를 전량 수집·적재하고 정리하는 Step 본체. 페이지 루프는 [SpotSyncTasklet]과 같다.
 * - 수집 호출에 실행일을 넘긴다(`searchFestival2` 필수 파라미터).
 * - 정리가 두 갈래다 — [SpotRepository.deleteMissingFestivals]와 [SpotRepository.deleteEndedFestivals].
 * - 실행일은 잡 파라미터(`runDate`)에서 읽는다 — `now()`를 부르면 자정을 걸친 실행에서 방금 적재한 축제를 지운다.
 */
class FestivalSyncTasklet(
    private val language: DomainLocale,
    private val syncPort: FestivalSyncPort,
    private val spotRepository: SpotRepository,
    // 생 SQL(upsert) 경로의 포트.
    private val pageSize: Int,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 이번 실행에서 수집한 contentId 전체. Step이 끝날 때 이 집합의 여집합이 사라진 축제다. */
    private val collectedIds = HashSet<String>()

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val runDate = chunkContext.runDate()
        val executionContext = chunkContext.stepContext.stepExecution.executionContext
        val pageNo = executionContext.getInt(KEY_NEXT_PAGE, 1)
        if (!executionContext.containsKey(KEY_RESUMED_FROM)) {
            executionContext.putInt(KEY_RESUMED_FROM, pageNo)
        }
        val syncedAt = executionContext.getString(KEY_SYNCED_AT, Instant.now().toString()).let(Instant::parse)

        val page = syncPort.fetchPage(language, runDate, pageNo, pageSize)
        val written = spotRepository.upsertFestivals(page.records, syncedAt)
        page.records.forEach { collectedIds += it.contentId }

        val totalWritten = executionContext.getInt(KEY_WRITTEN, 0) + written
        val totalDropped = executionContext.getInt(KEY_DROPPED, 0) + page.droppedCount

        executionContext.putString(KEY_SYNCED_AT, syncedAt.toString())
        executionContext.putInt(KEY_WRITTEN, totalWritten)
        executionContext.putInt(KEY_DROPPED, totalDropped)
        contribution.incrementWriteCount(written.toLong())

        val hasMore = page.hasMore(pageNo, pageSize)
        executionContext.putInt(KEY_NEXT_PAGE, if (hasMore) pageNo + 1 else pageNo)

        if (!hasMore) {
            log.info(
                "festival sync completed: language={} written={} dropped={}",
                language,
                totalWritten,
                totalDropped,
            )
            executionContext.putInt(KEY_COLLECTED, collectedIds.size)
            cleanup(executionContext, runDate)
        }

        return if (hasMore) RepeatStatus.CONTINUABLE else RepeatStatus.FINISHED
    }

    /**
     * 축제 정리. 수집이 모두 끝난 뒤 한 번만 한다. 두 게이트에 걸리면 종료 축제 삭제도 건너뛴다:
     *  1. 재개 실행이 아니다 — 재시작하면 수집 집합이 불완전하다.
     *  2. 0건이 아니다 — 업스트림 일시 오류로 빈 목록을 받으면 그 언어의 축제가 전량 삭제된다.
     */
    private fun cleanup(executionContext: ExecutionContext, runDate: LocalDate) {
        val collected = collectedIds.size

        val skipReason = when {
            executionContext.getInt(KEY_RESUMED_FROM, 1) > 1 -> "resumed run (incomplete id set)"
            collected == 0 -> "collected 0 items"
            else -> null
        }

        if (skipReason != null) {
            log.warn("festival cleanup skipped: language={} reason={}", language, skipReason)
            executionContext.putString(KEY_CLEANUP_SKIPPED, skipReason)
            return
        }

        // 두 삭제 대상은 겹친다 — deleteEnded가 먼저여야 삭제 사유별 건수가 섞이지 않는다.
        val endedDeleted = spotRepository.deleteEndedFestivals(language, runDate)
        val missingDeleted = spotRepository.deleteMissingFestivals(language, collectedIds)
        val deleted = endedDeleted + missingDeleted

        log.info(
            "festival cleanup: language={} deleted={} (ended={} missing={}) surviving={}",
            language,
            deleted,
            endedDeleted,
            missingDeleted,
            collected,
        )
        executionContext.putInt(KEY_DELETED, deleted)
        executionContext.putInt(KEY_DELETED_ENDED, endedDeleted)
    }

    companion object {
        const val KEY_NEXT_PAGE = "nextPage"
        const val KEY_RESUMED_FROM = "resumedFrom"
        const val KEY_CLEANUP_SKIPPED = "cleanupSkipped"
        const val KEY_DELETED = "deleted"
        const val KEY_DELETED_ENDED = "deletedEnded"
        const val KEY_SYNCED_AT = "syncedAt"
        const val KEY_WRITTEN = "written"
        const val KEY_DROPPED = "dropped"
        const val KEY_COLLECTED = "collected"
    }
}
