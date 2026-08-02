package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.client.SpotSyncPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
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
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val executionContext = chunkContext.stepContext.stepExecution.executionContext
        val pageNo = executionContext.getInt(KEY_NEXT_PAGE, 1)
        // 실행 시각은 첫 페이지에서 한 번 고정한다. 페이지마다 now()를 쓰면 정리 단계의 기준 시각이
        // 페이지별로 어긋나 "이번 실행이 건드린 행"의 경계가 흐려진다.
        val syncedAt = executionContext.getString(KEY_SYNCED_AT, Instant.now().toString()).let(Instant::parse)

        val page = syncPort.fetchPage(language, pageNo, pageSize)
        val written = storePort.upsertAll(page.records, syncedAt)

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
            // 전량 성공 여부를 정리 단계가 읽는다(설계 문서 §2.6의 게이트).
            executionContext.putInt(KEY_COLLECTED, totalWritten)
        }

        return if (hasMore) RepeatStatus.CONTINUABLE else RepeatStatus.FINISHED
    }

    companion object {
        const val KEY_NEXT_PAGE = "nextPage"
        const val KEY_SYNCED_AT = "syncedAt"
        const val KEY_WRITTEN = "written"
        const val KEY_DROPPED = "dropped"
        const val KEY_CATEGORY_FALLBACK = "categoryFallback"
        const val KEY_COLLECTED = "collected"
    }
}
