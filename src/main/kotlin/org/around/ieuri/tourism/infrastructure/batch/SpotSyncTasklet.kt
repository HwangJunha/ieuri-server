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

/**
 * 한 언어의 관광지 목록을 전량 수집·적재하는 Step 본체(SPEC-0004 R-2·R-3·R-4).
 *
 * **ItemReader/Processor/Writer 대신 Tasklet인 이유.** 표준 청크 모델은 "아이템 스트림"을 전제하는데,
 * 여기서 재개 단위는 아이템이 아니라 **페이지**다(AC-8: 실패 지점부터 재개). 페이지 번호를
 * ExecutionContext에 직접 들고 루프를 도는 편이 페이지 경계를 커밋 경계와 일치시키기 쉽고, 페이지당
 * 한 번 upsert하는 구조와도 맞는다. ItemReader로 페이징을 흉내 내면 재개 지점 표현이 오히려 복잡해진다.
 *
 * **페이지마다 커밋한다**(RepeatStatus.CONTINUABLE 반환). Spring Batch가 tasklet 1회 실행을 1 트랜잭션으로
 * 감싸므로, 페이지 하나가 실패해도 앞 페이지는 커밋된 상태로 남는다(AC-7). 실패 시 ExecutionContext에
 * 저장된 다음 페이지 번호가 그대로 남아 재실행이 거기서 이어진다(AC-8).
 *
 * 언어 간 병렬 실행은 하지 않는다 — serviceKey가 4개 언어 공용 단일 키라(SPEC-0002 §0 B-4 실측)
 * 병렬화가 호출 한도를 4배 속도로 소모한다.
 */
class SpotSyncTasklet(
    private val language: DomainLocale,
    private val syncPort: SpotSyncPort,
    private val storePort: SpotStorePort,
    private val pageSize: Int,
    private val cleanupMinRatio: Double,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이번 실행에서 **수집한 contentId 전체**. Step이 끝날 때 이 집합에 없는 행이 곧 업스트림에서
     * 사라진 항목이다(R-5).
     *
     * ExecutionContext가 아니라 인스턴스 필드에 두는 이유는 크기다 — 언어당 최대 6만 건이라
     * 매 페이지마다 직렬화해 DB에 쓰면 배치가 느려지고 메타 테이블이 비대해진다. 대신 **재시작하면
     * 집합이 비므로 정리를 건너뛴다**(아래 게이트가 그것을 판정한다). 정리는 다음 정상 실행이 한다.
     */
    private val collectedIds = HashSet<String>()

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val executionContext = chunkContext.stepContext.stepExecution.executionContext
        val pageNo = executionContext.getInt(KEY_NEXT_PAGE, 1)
        // 이 Step이 몇 페이지에서 시작했는지 한 번만 기록한다. 1보다 크면 재개 실행이라
        // 수집 집합이 불완전하므로 정리를 건너뛴다.
        if (!executionContext.containsKey(KEY_RESUMED_FROM)) {
            executionContext.putInt(KEY_RESUMED_FROM, pageNo)
        }
        // 실행 시각은 첫 페이지에서 한 번 고정한다. 페이지마다 now()를 쓰면 정리 단계의 기준 시각이
        // 페이지별로 어긋나 "이번 실행이 건드린 행"의 경계가 흐려진다.
        val syncedAt = executionContext.getString(KEY_SYNCED_AT, Instant.now().toString()).let(Instant::parse)

        val page = syncPort.fetchPage(language, pageNo, pageSize)
        val written = storePort.upsertAll(page.records, syncedAt)
        page.records.forEach { collectedIds += it.contentId }

        val totalWritten = executionContext.getInt(KEY_WRITTEN, 0) + written
        val totalDropped = executionContext.getInt(KEY_DROPPED, 0) + page.droppedCount
        val totalFallback = executionContext.getInt(KEY_CATEGORY_FALLBACK, 0) + page.categoryFallbackCount

        executionContext.putString(KEY_SYNCED_AT, syncedAt.toString())
        executionContext.putInt(KEY_WRITTEN, totalWritten)
        executionContext.putInt(KEY_DROPPED, totalDropped)
        executionContext.putInt(KEY_CATEGORY_FALLBACK, totalFallback)
        contribution.incrementWriteCount(written.toLong())

        val hasMore = page.hasMore(pageNo, pageSize)
        executionContext.putInt(KEY_NEXT_PAGE, if (hasMore) pageNo + 1 else pageNo)

        if (!hasMore) {
            // 언어별 처리 건수는 AC-18이 요구하는 값이다. 폴백 건수가 크면 데이터가 아니라
            // categoryCodes 설정이 잘못됐다는 신호다(설계 문서 §2.3).
            log.info(
                "spot sync completed: language={} written={} dropped={} categoryFallback={}",
                language, totalWritten, totalDropped, totalFallback,
            )
            executionContext.putInt(KEY_COLLECTED, collectedIds.size)
            cleanup(executionContext, pageNo)
        }

        return if (hasMore) RepeatStatus.CONTINUABLE else RepeatStatus.FINISHED
    }

    /**
     * 사라진 항목 정리(R-5). **수집 집합의 여집합을 지운다** — 시각 기준이 아니라 집합 기준이다.
     *
     * `arrange=Q`(수정일순)는 페이지를 읽는 중 업스트림이 갱신되면 정렬이 이동해 **페이지 경계
     * 항목이 누락 수집**될 수 있다. 누락은 곧 "사라진 것"으로 오인되므로, 정리를 실행할지 자체를
     * 세 겹으로 막는다:
     *
     *  1. **재개 실행 아님** — 재시작하면 앞 페이지를 다시 읽지 않아 집합이 불완전하다.
     *  2. **0건 아님** — 업스트림 일시 오류로 빈 목록을 받으면 전량 삭제된다(AC-14).
     *  3. **급감 아님** — 기존 대비 [cleanupMinRatio] 미만이면 정렬 이동이 아니라 업스트림 이상이다.
     *
     * 게이트에 걸리면 삭제를 건너뛰고 사유를 실행 이력에 남긴다 — 조용히 넘어가면 "왜 안 지워졌나"를
     * 나중에 알 수 없다.
     */
    private fun cleanup(executionContext: ExecutionContext, lastPageNo: Int) {
        val existing = storePort.countByLanguage(language)
        val collected = collectedIds.size

        val skipReason = when {
            // 1페이지부터 시작하지 않았다면 재개 실행이다 — 집합에 앞 페이지가 없다.
            executionContext.getInt(KEY_RESUMED_FROM, 1) > 1 ->
                "resumed run (incomplete id set)"
            collected == 0 -> "collected 0 items"
            existing > 0 && collected < existing * cleanupMinRatio ->
                "collected $collected < ${"%.0f".format(cleanupMinRatio * 100)}% of existing $existing"
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
        const val KEY_COLLECTED = "collected"
    }
}
