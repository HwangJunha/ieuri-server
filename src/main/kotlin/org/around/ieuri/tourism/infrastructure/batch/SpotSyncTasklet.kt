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
 *
 * @param endedFestivalIds 종료된 축제의 contentId를 돌려주는 조회(SPEC-0007 R-9).
 *   `areaBasedSyncList2`에는 `contentTypeId` 요청 파라미터가 없어 **축제도 이 경로로 수집된다.**
 *   제외하지 않으면 축제 정리가 지운 행사를 이 Step이 그대로 되살린다(AC-31·AC-32).
 *
 *   **집합이 아니라 함수를 받는 이유는 Step 빈이 부팅 시점에 만들어지기 때문이다.** 그때 조회한
 *   집합은 실행 시점에는 낡았다. 대신 Step이 시작할 때 한 번 호출해 스냅샷을 잡는다 — 축제는 절대
 *   건수가 적어 페이지마다 다시 조회할 이유가 없다.
 */
class SpotSyncTasklet(
    private val language: DomainLocale,
    private val syncPort: SpotSyncPort,
    private val storePort: SpotStorePort,
    private val pageSize: Int,
    private val cleanupMinRatio: Double,
    private val endedFestivalIds: (LocalDate) -> Set<String> = { emptySet() },
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

    /**
     * 종료 축제 제외 집합의 스냅샷(R-9). Step이 시작할 때 한 번만 잡는다 — 페이지마다 다시 조회하면
     * 페이지당 질의가 하나씩 늘고, 하루 1회 배치에서 그만큼의 신선도가 필요하지도 않다.
     */
    private var endedFestivalSnapshot: Set<String>? = null

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

        // 종료된 축제를 적재 대상에서 뺀다(SPEC-0007 R-9). **어댑터가 아니라 여기서 거른다** —
        // 어댑터에서 걸러 버리면 제외분이 page.records에 아예 없어 아래 collectedIds에도 들어가지
        // 못하고, 그것이 AC-34 문제를 그대로 일으킨다.
        //
        // 판정은 contentId **집합 멤버십**뿐이다. 카테고리나 날짜를 여기서 다시 해석하지 않으므로
        // 축제가 아닌 항목은 영향을 받지 않는다(AC-33) — 축제가 아닌 contentId는 애초에 이 집합에
        // 들어올 수 없다.
        val excluded = endedFestivalSnapshot
            ?: endedFestivalIds(chunkContext.runDate()).also { endedFestivalSnapshot = it }
        val toStore = if (excluded.isEmpty()) page.records else page.records.filterNot { it.contentId in excluded }
        val written = storePort.upsertAll(toStore, syncedAt)

        // **제외분까지 collectedIds에 넣는다**(AC-34). 이 집합은 정리에서 두 곳에 쓰인다 —
        // deleteMissing의 생존 집합이자 급감 게이트의 분자다. 제외분이 빠지면 (1) 수집량이 실제보다
        // 작아져 관광지 정리가 부당하게 스킵될 수 있고, (2) deleteMissing이 제외분을 "사라진 항목"으로
        // 보고 삭제 대상에 넣어 삭제 건수가 이중으로 잡힌다. 축제 Step이 이미 지운 행이라 최종 상태는
        // 같지만 관측값이 틀어진다.
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
