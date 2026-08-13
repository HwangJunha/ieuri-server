package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.time.Instant
import java.time.LocalDate

/**
 * 한 언어의 관광지 목록을 전량 수집·적재하고 사라진 항목을 정리하는 Step 본체.
 *
 * 페이지 단위로 루프를 돌며 다음 페이지 번호를 ExecutionContext에 남긴다. `RepeatStatus.CONTINUABLE`
 * 반환마다 Spring Batch가 커밋하므로, 한 페이지가 실패해도 앞 페이지는 남고 재실행이 거기서 이어진다.
 *
 * @param endedFestivalIds 적재 대상에서 뺄 종료 축제의 contentId 조회. 집합이 아니라 함수로 받는다 —
 *   Step 빈은 부팅 시점에 만들어져 그때 조회한 집합은 실행 시점에 낡는다.
 */
class SpotSyncTasklet(
    private val language: DomainLocale,
    private val syncPort: SpotSyncPort,
    private val storePort: SpotStorePort,
    private val pageSize: Int,
    private val endedFestivalIds: (LocalDate) -> Set<String> = { emptySet() },
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이번 실행에서 수집한 contentId 전체. Step이 끝날 때 이 집합에 없는 행이 업스트림에서 사라진
     * 항목이다. ExecutionContext가 아니라 인스턴스 필드이므로 **재시작하면 비어 있고**, 그 경우
     * 아래 게이트가 정리를 건너뛴다.
     */
    private val collectedIds = HashSet<String>()

    /** 종료 축제 제외 집합의 스냅샷. Step이 시작할 때 한 번만 잡는다. */
    private var endedFestivalSnapshot: Set<String>? = null

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val executionContext = chunkContext.stepContext.stepExecution.executionContext
        val pageNo = executionContext.getInt(KEY_NEXT_PAGE, 1)
        // 이 Step이 몇 페이지에서 시작했는지 한 번만 기록한다. 1보다 크면 재개 실행이라
        // 수집 집합이 불완전하므로 정리를 건너뛴다.
        if (!executionContext.containsKey(KEY_RESUMED_FROM)) {
            executionContext.putInt(KEY_RESUMED_FROM, pageNo)
        }
        // 실행 시각은 첫 페이지에서 한 번 고정한다. 페이지마다 now()를 쓰면 "이번 실행이 건드린 행"의
        // 경계가 흐려진다.
        val syncedAt = executionContext.getString(KEY_SYNCED_AT, Instant.now().toString()).let(Instant::parse)

        val page = syncPort.fetchPage(language, pageNo, pageSize)

        // 종료된 축제를 적재 대상에서 뺀다. 어댑터가 아니라 여기서 걸러야 한다 — 어댑터에서 걸면
        // 제외분이 page.records에 없어 아래 collectedIds에도 들어가지 못한다.
        val excluded = endedFestivalSnapshot
            ?: endedFestivalIds(chunkContext.runDate()).also { endedFestivalSnapshot = it }
        val toStore = if (excluded.isEmpty()) page.records else page.records.filterNot { it.contentId in excluded }
        val written = storePort.upsertAll(toStore, syncedAt)

        // toStore가 아니라 page.records다 — 종료 축제 제외분까지 넣어야 한다. 이 집합은
        // deleteMissing의 생존 집합이라, 빠지면 그 항목이 "사라진 항목"으로 삭제된다.
        //
        // **비관광 제외분(SPEC-0018)은 반대로 여기 없어야 한다** — 어댑터가 page.records에서
        // 이미 뺐고, 그래야 이전에 적재됐던 행이 정리 단계에서 지워진다. 그것이 이 방식의 요점이다.
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
            // 폴백 건수가 크면 데이터가 아니라 categoryCodes 설정이 잘못됐다는 신호다.
            // 제외(excluded) 건수도 마찬가지로 키워드 설정 오류의 신호다(SPEC-0018).
            log.info(
                "spot sync completed: language={} written={} dropped={} categoryFallback={} excluded={}",
                language, totalWritten, totalDropped, totalFallback, totalExcluded,
            )
            executionContext.putInt(KEY_COLLECTED, collectedIds.size)
            cleanup(executionContext, pageNo)
        }

        return if (hasMore) RepeatStatus.CONTINUABLE else RepeatStatus.FINISHED
    }

    /**
     * 사라진 항목 정리 — 수집 집합의 여집합을 지운다.
     *
     * 삭제 전 두 겹의 게이트를 통과해야 한다. **어느 하나를 빼면 대량 오삭제가 된다:**
     *  1. **재개 실행 아님** — 재시작하면 앞 페이지를 다시 읽지 않아 집합이 불완전하다.
     *  2. **0건 아님** — 업스트림 일시 오류로 빈 목록을 받으면 전량 삭제된다.
     *
     * 게이트에 걸리면 삭제를 건너뛰고 사유를 실행 이력에 남긴다.
     */
    private fun cleanup(executionContext: ExecutionContext, lastPageNo: Int) {
        val collected = collectedIds.size

        val skipReason = when {
            // 1페이지부터 시작하지 않았다면 재개 실행이다 — 집합에 앞 페이지가 없다.
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

        val deleted = storePort.deleteMissing(language, collectedIds)
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
