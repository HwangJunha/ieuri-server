package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.client.FestivalSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.FestivalStorePort
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
 * 한 언어의 축제 정보를 전량 수집·적재하고 정리하는 Step 본체(SPEC-0007 R-3·R-6·R-7·R-8·R-10).
 *
 * 구조는 [SpotSyncTasklet]과 같다 — 페이지 루프를 Tasklet이 직접 돌고, 다음 페이지 번호를
 * ExecutionContext에 남겨 실패 지점부터 재개한다. 같은 잡 안에서 두 Step이 다른 모양을 하고 있을
 * 이유가 없다.
 *
 * **[SpotSyncTasklet]과 다른 점은 셋이다.**
 *  1. 수집 호출에 **실행일**을 함께 넘긴다(AC-8). `searchFestival2`의 필수 파라미터다.
 *  2. 정리에 **급감 비율 게이트를 적용하지 않는다**(R-10·AC-36). 축제는 절대 건수가 적어 계절에 따라
 *     절반 이하로 흔들리는 것이 정상이며, 비율 게이트를 걸면 정리가 영구히 막힌다.
 *  3. 정리가 **두 갈래**다 — 이번 수집에 없는 행([FestivalStorePort.deleteMissing])과 이미 종료된 행
 *     ([FestivalStorePort.deleteEnded]). 둘 다 `spot` 부모 행을 지우고 축제 정보는 CASCADE로 따라간다.
 *
 * **실행일을 잡 파라미터(`runDate`)에서 읽고 `LocalDate.now()`를 부르지 않는다.** 런처가 이미 그 값을
 * 식별 파라미터로 넣고 있으므로(SPEC-0004 AC-6의 중복 실행 방지 기준) 수집 조건·종료 판정·재삽입
 * 제외 집합이 모두 같은 날짜를 쓴다. Step마다 `now()`를 부르면 자정을 걸쳐 도는 실행에서 단계마다
 * 날짜가 달라져, 방금 적재한 축제를 같은 실행의 정리가 지운다.
 */
class FestivalSyncTasklet(
    private val language: DomainLocale,
    private val syncPort: FestivalSyncPort,
    private val storePort: FestivalStorePort,
    private val pageSize: Int,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 이번 실행에서 수집한 contentId 전체. Step이 끝날 때 이 집합의 여집합이 사라진 축제다(AC-29). */
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
        val written = storePort.upsertAll(page.records, syncedAt)
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
            // 드랍 건수를 적재 건수와 구분해 남기는 것이 [TBD-2]의 해소 수단이다(AC-39·AC-41).
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
     * 게이트는 두 겹뿐이다([SpotSyncTasklet.cleanup]의 세 겹에서 급감 비율이 빠진다, AC-36):
     *  1. **재개 실행 아님**(AC-37) — 앞 페이지를 다시 읽지 않아 수집 집합이 불완전하다.
     *  2. **0건 아님**(AC-35) — 업스트림 일시 오류로 빈 목록을 받으면 그 언어의 축제가 전량 삭제된다.
     *
     * **게이트에 걸리면 종료 축제 삭제도 함께 건너뛴다.** 종료 삭제 자체는 수집 집합과 무관하지만,
     * 두 삭제를 분리하면 "수집이 실패한 날에도 삭제는 돌았다"는 상태가 생겨 사후 추적이 어려워진다.
     * 하루 미루는 비용이 그보다 싸다.
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

        // **두 삭제는 겹친다.** 종료된 축제는 애초에 수집되지 않으므로(AC-9) 수집 집합의 여집합에도
        // 들어 있어, [deleteMissing] 하나만으로도 결국 지워진다. 그럼에도 [deleteEnded]를 따로
        // 부르는 이유는 **삭제 사유를 건수로 구분해 남기기 위해서다**(AC-40) — "끝나서 지웠다"와
        // "업스트림에서 사라져서 지웠다"는 운영상 전혀 다른 신호이며, 합계만 남기면 구분할 수 없다.
        // 먼저 부르므로 종료분이 endedDeleted에 잡히고, 이어지는 deleteMissing은 나머지만 센다.
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
