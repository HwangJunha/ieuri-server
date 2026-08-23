package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.client.FestivalSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.FestivalStorePort
import org.around.ieuri.tourism.application.port.out.persistence.FestivalUpsertPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.time.Instant
import java.time.LocalDate

/**
 * 한 언어의 축제 정보를 전량 수집·적재하고 정리하는 Step 본체. 페이지 루프 구조는 [SpotSyncTasklet]과
 * 같고, [SpotSyncTasklet]과 다른 점은 셋이다.
 *  1. 수집 호출에 실행일을 함께 넘긴다 — `searchFestival2`의 필수 파라미터다.
 *  2. 정리에 급감 비율 게이트를 적용하지 않는다. 축제는 계절에 따라 절반 이하로 흔들리는 것이
 *     정상이라, 비율 게이트를 걸면 정리가 영구히 막힌다.
 *  3. 정리가 두 갈래다 — [FestivalStorePort.deleteMissing]과 [FestivalStorePort.deleteEnded].
 *
 * **실행일은 잡 파라미터(`runDate`)에서 읽고 `LocalDate.now()`를 부르지 않는다** — Step마다 `now()`를
 * 부르면 자정을 걸쳐 도는 실행에서 방금 적재한 축제를 같은 실행의 정리가 지운다.
 */
class FestivalSyncTasklet(
    private val language: DomainLocale,
    private val syncPort: FestivalSyncPort,
    private val storePort: FestivalStorePort,
    // 생 SQL(upsert) 경로는 포트가 갈린다 — 어느 쓰기가 그쪽인지 여기서 보인다.
    private val upsertPort: FestivalUpsertPort,
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
        val written = upsertPort.upsertAll(page.records, syncedAt)
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
                language, totalWritten, totalDropped,
            )
            executionContext.putInt(KEY_COLLECTED, collectedIds.size)
            cleanup(executionContext, runDate)
        }

        return if (hasMore) RepeatStatus.CONTINUABLE else RepeatStatus.FINISHED
    }

    /**
     * 축제 정리. **수집이 모두 끝난 뒤 한 번만** 수행한다 — 수집 중간에 지우면 페이지 경계에서 방금
     * 넣은 행을 지울 수 있다.
     *
     * 게이트는 두 겹이다:
     *  1. **재개 실행 아님** — 앞 페이지를 다시 읽지 않아 수집 집합이 불완전하다.
     *  2. **0건 아님** — 업스트림 일시 오류로 빈 목록을 받으면 그 언어의 축제가 전량 삭제된다.
     *
     * 게이트에 걸리면 종료 축제 삭제도 함께 건너뛴다.
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

        // 두 삭제 대상은 겹치며, 순서가 건수 배분을 정한다 — deleteEnded를 먼저 부르므로 종료분이
        // endedDeleted에 잡히고 이어지는 deleteMissing은 나머지만 센다. 순서를 바꾸면 삭제 사유별
        // 관측값이 뒤섞인다.
        val endedDeleted = storePort.deleteEnded(language, runDate)
        val missingDeleted = storePort.deleteMissing(language, collectedIds)
        val deleted = endedDeleted + missingDeleted

        log.info(
            "festival cleanup: language={} deleted={} (ended={} missing={}) surviving={}",
            language, deleted, endedDeleted, missingDeleted, collected,
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
